# Chunk Terrain Shaping

## Problem Statement

When rendering a chunk, we need to determine the Y-level of stone (terrain floor) for each block position. Rivers influence terrain significantly: they carve riverbeds, shape banks, and create smooth transitions to surrounding terrain.

A river can be up to 40 blocks wide. Its influence zone (including banks and transitions) can extend up to 120 blocks from the river centerline. This means any given block might be influenced by multiple cells across multiple rivers.

We need an algorithm that:
- Combines influences from multiple cells reliably
- Produces smooth, natural-looking terrain
- Is deterministic and order-independent
- Prioritizes riverbeds over embankments when they conflict

## Unit Reference

- **Block**: Single position {x, z}
- **Chunk**: 16×16 blocks (256 positions)
- **Cell**: 3×3 chunks = 48×48 blocks (2,304 positions)
- **Region**: 32×32 cells

## Prerequisites

By the time we're shaping terrain for a chunk, the following is already known:
- Which cell each block belongs to
- The feature type of every cell (Waterfall, Spring, Delta, etc.)
- The Y-level of water at each cell's center point
- Flow direction, upstream/downstream relationships
- Width, depth, and descent angle (calculable from watershed data)

## River Path Geometry

Within a cell (48×48 blocks, 0-indexed), a river follows this pattern:

### Entry and Exit Points

Water enters and exits a cell from one of two location types:

- **Cardinal centerpoint**: Blocks 23-24 along one axis, at edge 0 or 47 on the other axis
  - North: {23, 0} and {24, 0}
  - South: {23, 47} and {24, 47}
  - West: {0, 23} and {0, 24}
  - East: {47, 23} and {47, 24}

- **Intercardinal corner**: One of the four extreme corners
  - Northwest: {0, 0}
  - Northeast: {47, 0}
  - Southwest: {0, 47}
  - Southeast: {47, 47}

### Center Constraint

Water **must** flow through the center of the cell. The center is defined as the four blocks:
- {23, 23}, {23, 24}, {24, 23}, {24, 24}

### Path Shape

The river path through a cell consists of two line segments:
1. **Entry → Center**: From the entry point to the cell center
2. **Center → Exit**: From the cell center to the exit point

This means the path can curve (entry and exit need not be opposite), but always passes through the middle.

### Y-Level Interpolation Along Path

- **At cell center**: Y-level is known directly from cell data
- **At entry/exit points**: Lerp between this cell's center Y and the adjacent cell's center Y
- **Along segments**: Lerp between entry/center or center/exit as appropriate

Features may override this default interpolation (e.g., Waterfall might plunge at center rather than lerp smoothly).

## Data Structures

### Shape

Each cell returns its "opinion" about terrain for positions in a chunk:

```java
record Shape(
    int y,              // proposed stone Y-level
    double weight,      // 0.0 to 1.0, how strongly this cell claims this position
    boolean isRiverbed  // true = carving riverbed, false = embankment/transition
)
```

**Nullable**: A cell returns `null` for positions it has no opinion about.

### Cell Output

Each cell implements a shaping function:

```java
Shape[][] shape(ChunkPos chunk)
```

Returns a 16×16 array of Shape (nullable per-position) representing this cell's opinion about every block in the requested chunk.

## Algorithm

### Step 1: Identify Contributing Cells

For each block position {x, z} in the chunk:

1. Determine which cells have any part within 120 blocks of this position
2. These are the contributing cells for this specific block

**Distance definition**: Euclidean distance from the block column {x, z} to the nearest point in the cell's bounding box. If any part of the cell is within 120 blocks, it's a potential contributor. This is a coarse filter; cells self-filter via null returns in `shape()`.

This is calculated per-block, not per-chunk. Different blocks within the same chunk may have different sets of contributing cells.

### Step 2: Collect Shapes

For each contributing cell, call `shape(chunkPos)` to get its 16×16 opinion array.

### Step 3: Combine Shapes Per-Position

For each of the 256 block positions in the chunk:

```
function combineShapes(shapes: List<Shape>, vanillaY: int): int
    // Filter out nulls
    shapes = shapes.filter(s -> s != null)

    if shapes.isEmpty()
        return vanillaY  // No opinion, use vanilla terrain

    // Partition by type
    riverbedShapes = shapes.filter(s -> s.isRiverbed)
    embankmentShapes = shapes.filter(s -> !s.isRiverbed)

    // Riverbeds take priority
    if riverbedShapes.isNotEmpty()
        (riverY, confidence) = powerWeightedAverageWithConfidence(riverbedShapes)
        return blendTowardVanilla(riverY, vanillaY, confidence)

    // Fall back to embankments
    (embankmentY, confidence) = weightedAverageWithConfidence(embankmentShapes)
    return blendTowardVanilla(embankmentY, vanillaY, confidence)
```

**Vanilla Y source**: Use `Sample.estimatedHeight()` from `org.sosly.rivertale.density.Sample` to get the estimated vanilla stone Y-level for the position. This is approximately 98% accurate.

### Step 4: Power-Weighted Average

The key insight: we want high-weight contributors to *dominate* without hard cutoffs. A power function achieves this.

```
SHARPNESS = 3  // Global constant, tunable

function powerWeightedAverageWithConfidence(shapes: List<Shape>): (int, double)
    sumY = 0.0
    sumWeight = 0.0
    maxWeight = 0.0

    for shape in shapes:
        adjusted = shape.weight ^ SHARPNESS
        sumY += shape.y * adjusted
        sumWeight += adjusted
        maxWeight = max(maxWeight, shape.weight)

    y = round(sumY / sumWeight)
    confidence = maxWeight  // Confidence = the strongest contributor's weight
    return (y, confidence)
```

**Why the exponent?**

| Weights | Regular Average | Sharpness=3 |
|---------|-----------------|-------------|
| 0.8, 0.4 | 66% / 33% | 89% / 11% |
| 0.9, 0.3 | 75% / 25% | 96% / 4% |
| 0.5, 0.5 | 50% / 50% | 50% / 50% |

The closer river dominates naturally. Transitions remain smooth as weights shift.

### Step 5: Standard Weighted Average (for embankments)

```
function weightedAverageWithConfidence(shapes: List<Shape>): (int, double)
    sumY = 0.0
    sumWeight = 0.0
    maxWeight = 0.0

    for shape in shapes:
        sumY += shape.y * shape.weight
        sumWeight += shape.weight
        maxWeight = max(maxWeight, shape.weight)

    y = round(sumY / sumWeight)
    confidence = maxWeight
    return (y, confidence)
```

### Step 6: Blend Toward Vanilla

When confidence is low, the river's opinion is weak and we should blend toward vanilla terrain for smooth edge transitions.

```
BLEND_THRESHOLD = 0.3  // Below this, start blending toward vanilla

function blendTowardVanilla(riverY: int, vanillaY: int, confidence: double): int
    if confidence >= BLEND_THRESHOLD
        return riverY  // River has full authority

    // Linear blend: at confidence=0, use vanilla; at threshold, use river
    blendFactor = confidence / BLEND_THRESHOLD
    return round(lerp(vanillaY, riverY, blendFactor))

function lerp(a: int, b: int, t: double): double
    return a + (b - a) * t
```

**Why this matters**: Without vanilla blending, a cell returning weight=0.1 would still fully override vanilla terrain. With blending, weak opinions gracefully fade into the surrounding landscape.

## What Features Must Implement

Each feature (Waterfall, Spring, Delta, etc.) implements `shape(ChunkPos)` and decides:

1. **Which positions it has opinions about** — based on distance from its river path
2. **The Y-level it proposes** — based on water Y-level, depth, bank profile
3. **The weight for each position** — typically based on distance from centerline
4. **Whether each position is riverbed or embankment**

### Weight Calculation

Left to each feature, but the general principle:

**Weight semantics**: Weight represents "how much authority does this cell have over this position" on a 0.0 to 1.0 scale:
- **1.0** = Core authority (centerline, riverbed floor)
- **0.5** = Transition zone (banks, slopes)
- **0.1** = Edge of influence (barely reaching this position)
- **0.0** = No authority (return `null` instead)

Guideline values:
- Positions on/near the river centerline: high weight (0.8–1.0)
- Positions in the transition zone: medium weight (0.3–0.7)
- Positions at the edge of influence: low weight (0.1–0.3)

The specific falloff curve (linear, smooth, stepped) is feature-dependent.

### Y-Level Along the Path

Each cell knows its center-point water Y-level. For positions along the entry→center→exit path:
- **Default**: Lerp between adjacent cell centers
- **Feature override**: Some features (e.g., Waterfall) may use custom interpolation

## Properties of This Algorithm

- **Deterministic**: Same inputs always produce same outputs
- **Order-independent**: Cells can be processed in any order
- **Non-directional**: No bias based on iteration direction
- **Smooth**: Power-weighted averaging prevents hard cutoffs
- **Prioritized**: Riverbeds always beat embankments at the same position

## Edge Cases

### No Contributing Cells
Return vanilla Y directly. The river system has no opinion about this position.

### Single Contributor
The algorithm still works; that cell's Y-level is used directly (weight normalization handles it).

### Two Rivers Equidistant
Power-weighted average blends them. With equal weights, Y-levels average. The sharpness exponent ensures that as you move toward one river, it quickly dominates.

### Riverbed and Embankment at Same Position
Riverbed wins entirely. Embankment shapes are ignored for that position.

### All Contributors Have Low Weight
If confidence (max weight) is below `BLEND_THRESHOLD`, the result blends toward vanilla. At confidence=0.15 with threshold=0.3, the result is 50% river opinion, 50% vanilla.

## Future Considerations

### Water Y-Level
This spec covers stone terrain. Water Y-level is a separate (simpler) calculation: once we know stone Y, water Y is derived from the cell's water level and depth data.

### Performance
If `shape()` calls prove expensive, consider:
- Caching shape results per cell (cells are larger than chunks)
- Lazy evaluation for positions far from any river
- Spatial indexing for contributing-cell lookup

## Open Questions

1. **Sharpness tuning**: Is 3 the right value? May need visual testing.
2. **Blend threshold tuning**: Is 0.3 the right value? May need visual testing.
3. **Influence radius**: Is 120 blocks correct for all features, or should some features have smaller influence zones?
