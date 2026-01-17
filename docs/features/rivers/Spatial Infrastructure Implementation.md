---
level: 5
parent: "[[Spatial Infrastructure]]"
status: draft
---

# Spatial Infrastructure — Implementation

This document provides implementation guidance for the [[Spatial Infrastructure]] specification. The spec is the source of truth; this document describes how to get there from the current code.

## Scope

This implementation covers all spatial data structures, coordinate types, caching infrastructure, and lazy services. It does NOT cover:
- Feature classification logic (see [[Feature Classification Implementation]])
- Region discovery or working sets (see [[Region Discovery Implementation]])
- Boundary identification algorithms (see [[Boundary Identification Implementation]])

## Overview

The current codebase was built during exploration. It works, but it diverged from the specification in several ways:

| Area | Current State | Target State |
|------|---------------|--------------|
| Configuration | Block-based units | Chunk/cell-based units |
| Sample | All 6 fields sampled at once | Two-tier lazy sampling |
| Cell | Single sample, single Y | Multi-sample, entry/exit Y, river properties |
| CellCache | Eager flow computation, runs classification | Lazy flow, no classification |
| FlowDirection | `rotateToward` rotates 90° | `rotateToward` rotates 45° |
| Feature enum | `Feature.DEFAULT` | `Feature.NONE` |
| Flow metric | `continents + depth` | `averageEstimatedTerrainHeight()` (depth-only) |

**Note:** The flow algorithm change from `continents + depth` to depth-based terrain height is intentional. The old approach conflated continental shelf position with terrain height; the new approach uses depth alone, which better represents actual terrain elevation. This is a deliberate behavior change, not just a refactor.

This refactor will break the codebase until complete. Plan for that.

## Definition of Done

**What "done" means for this spec:**

- The code compiles
- All unit tests in this spec pass
- Diagnostic commands (`/rivertale sample`, `/rivertale cell`, `/rivertale metrics`) work

**What "done" does NOT mean:**

- Rivers do not generate
- The world loads but rivers are absent
- Most of the pipeline is disconnected

This is expected and correct. Spatial Infrastructure is the foundation layer. After completing this spec, we have the data structures and caches that the rest of the pipeline needs, but the pipeline itself is not yet rebuilt.

**The path to working rivers again:**

```
Spatial Infrastructure  ← you are here
    ↓
Region Discovery
    ↓
Boundary Identification
    ↓
Feature Classification
    ↓
Flowline Tracing
    ↓
Watershed Building
    ↓
Watershed Validation
    ↓
Elevation Assignment
    ↓
Flow Accumulation
    ↓
Spline Building
    ↓
Terrain Shaping
    ↓
Water Placement
    ↓
Rivers work again
```

Each spec builds on the previous. Rivers won't flow until the entire pipeline is reconnected. That's okay. The goal for THIS implementation is a solid foundation—compiling code with passing tests.

## Implementation Sequence

Work through these phases in order. Each phase should compile (with test failures) before moving to the next.

```
Phase 1: Standalone Additions
    ├── FlowDirection fixes
    ├── New types (Boundary, BoundaryType)
    └── Coordinate helper methods

Phase 2: Configuration Units
    ├── CommonConfig restructure
    ├── CellPos block calculation update
    └── RegionPos block calculation update

Phase 3: Sample Restructure
    ├── Two-tier field structure
    ├── Lazy enrichment methods
    └── SampleCache two-tier sampling

Phase 4: Cell Restructure
    ├── Multi-sample support
    ├── New fields (entryY/exitY, width/depth, terminus, etc.)
    ├── Averaging methods
    └── Mutation methods

Phase 5: Cache Behavior
    ├── CellCache multi-sample selection
    ├── CellCache lazy flow computation
    ├── CellCache remove classification
    └── RegionCache remove path computation
```

---

## Phase 1: Standalone Additions

These changes don't break existing code. Get them done first.

### 1.1 FlowDirection Fixes

**File:** `core/FlowDirection.java`

#### Fix rotateToward

The current implementation rotates by 90° (2 compass indices). The spec requires 45° (1 compass index).

**Current behavior (wrong):**
```
NORTH.rotateToward(EAST) → EAST  (skips NORTHEAST)
```

**Target behavior:**
```
NORTH.rotateToward(EAST) → NORTHEAST  (one step toward target)
```

**Algorithm:**
```
rotateToward(target):
    if this == NONE or target == NONE or this == target:
        return this

    currentIdx = compassIndex(this)
    targetIdx = compassIndex(target)

    diff = (targetIdx - currentIdx + 8) % 8

    if diff == 0:
        return this
    else if diff <= 4:
        // Target is clockwise; rotate clockwise by 1
        return COMPASS[(currentIdx + 1) % 8]
    else:
        // Target is counter-clockwise; rotate counter-clockwise by 1
        return COMPASS[(currentIdx + 7) % 8]
```

The fix: change `+ 2` to `+ 1` and `+ 6` to `+ 7`.

#### Add toward static method

**New method:**
```
static toward(from: CellPos, to: CellPos): FlowDirection
    dx = to.x - from.x
    dz = to.z - from.z

    if dx == 0 and dz == 0:
        return NONE

    // Normalize to -1, 0, or 1
    ndx = (dx == 0) ? 0 : (dx > 0) ? 1 : -1
    ndz = (dz == 0) ? 0 : (dz > 0) ? 1 : -1

    // Find matching D8 direction
    for dir in D8:
        if dir.dx == ndx and dir.dz == ndz:
            return dir

    return NONE  // Should never reach
```

#### Tests for FlowDirection

| Test | Input | Expected |
|------|-------|----------|
| rotateToward same | `NORTH.rotateToward(NORTH)` | `NORTH` |
| rotateToward clockwise | `NORTH.rotateToward(EAST)` | `NORTHEAST` |
| rotateToward counter-clockwise | `NORTH.rotateToward(WEST)` | `NORTHWEST` |
| rotateToward opposite | `NORTH.rotateToward(SOUTH)` | `NORTHEAST` or `NORTHWEST` (either valid) |
| toward diagonal | `toward(CellPos(0,0), CellPos(5,5))` | `SOUTHEAST` |
| toward cardinal | `toward(CellPos(0,0), CellPos(0,10))` | `SOUTH` |
| toward same | `toward(CellPos(0,0), CellPos(0,0))` | `NONE` |

### 1.2 New Types

#### BoundaryType enum

**New file:** `core/BoundaryType.java`

```
enum BoundaryType {
    OCEAN,   // boundary between land and ocean
    BASIN    // boundary around an internal drainage basin
}
```

#### Boundary record

**New file:** `core/Boundary.java`

```
record Boundary(
    List<CellPos> cells,    // cells along boundary (iteration order, not geographic)
    BoundaryType type
) {}
```

These types are used by Boundary Identification. They need to exist before that spec can be implemented.

### 1.3 Coordinate Helper Methods

#### CellPos.contains(BlockPos)

**Add to:** `core/CellPos.java`

```
contains(pos: BlockPos): boolean
    return pos.getX() >= getMinBlockX()
        and pos.getX() < getMaxBlockX()
        and pos.getZ() >= getMinBlockZ()
        and pos.getZ() < getMaxBlockZ()
```

Note: `getMaxBlockX()` is exclusive (first block of next cell).

#### RegionPos.contains(BlockPos)

**Add to:** `core/RegionPos.java`

```
contains(pos: BlockPos): boolean
    return pos.getX() >= getMinBlockX()
        and pos.getX() < getMaxBlockX()
        and pos.getZ() >= getMinBlockZ()
        and pos.getZ() < getMaxBlockZ()
```

#### RegionPos.containsOutOfBoundsCells()

**Add to:** `core/RegionPos.java`

Minecraft's world boundary is ±30,000,000 blocks. Cells or regions near this boundary may reference positions outside valid world space.

```
containsOutOfBoundsCells(): boolean
    WORLD_LIMIT = 30_000_000

    return getMinBlockX() < -WORLD_LIMIT
        or getMaxBlockX() > WORLD_LIMIT
        or getMinBlockZ() < -WORLD_LIMIT
        or getMaxBlockZ() > WORLD_LIMIT
```

#### RegionPos.getBorderCells(FlowDirection)

**Add to:** `core/RegionPos.java`

Returns cells along one edge of the region. Only accepts cardinal directions.

```
getBorderCells(edgeDirection: FlowDirection): List<CellPos>
    if edgeDirection not in D4:
        throw IllegalArgumentException("Edge direction must be cardinal")

    minCell = getMinCell()
    cells = new ArrayList(cellsPerRegion())

    switch edgeDirection:
        NORTH:
            for i in 0 until cellsPerRegion():
                cells.add(CellPos(minCell.x + i, minCell.z))
        SOUTH:
            for i in 0 until cellsPerRegion():
                cells.add(CellPos(minCell.x + i, minCell.z + cellsPerRegion() - 1))
        WEST:
            for i in 0 until cellsPerRegion():
                cells.add(CellPos(minCell.x, minCell.z + i))
        EAST:
            for i in 0 until cellsPerRegion():
                cells.add(CellPos(minCell.x + cellsPerRegion() - 1, minCell.z + i))

    return cells
```

#### Tests for Coordinate Helpers

| Test | Setup | Expected |
|------|-------|----------|
| CellPos.contains inside | CellPos(0,0), BlockPos(24, 64, 24) | true |
| CellPos.contains outside | CellPos(0,0), BlockPos(100, 64, 100) | false |
| CellPos.contains boundary | CellPos(0,0), BlockPos(48, 64, 0) | false (exclusive) |
| RegionPos.getBorderCells NORTH | RegionPos(0,0) | 32 cells, all with z == minCell.z |
| RegionPos.getBorderCells count | Any direction | Exactly cellsPerRegion() cells |
| RegionPos.containsOutOfBoundsCells normal | RegionPos(0,0) | false |
| RegionPos.containsOutOfBoundsCells edge | RegionPos near world limit | true |

### 1.4 Feature Enum Rename

**File:** `cell/Feature.java`

Rename `Feature.DEFAULT` to `Feature.NONE` for clarity. The enum value represents "no feature classification has been assigned" rather than "this is the default feature type."

```
// Before
DEFAULT,

// After
NONE,
```

Update all usages in the codebase. This is a simple find-and-replace: `Feature.DEFAULT` → `Feature.NONE`.

---

## Phase 2: Configuration Units

Change configuration from block-based to chunk/cell-based units.

### 2.1 CommonConfig Restructure

**File:** `config/CommonConfig.java`

#### Record field changes

| Current | New | Notes |
|---------|-----|-------|
| `regionSize: int` (blocks) | `regionSize: int` (cells) | Default 32 |
| `cellSize: int` (blocks) | `cellSize: int` (chunks) | Default 3 |
| — | `samplesPerCell: int` | Default 1, range 1 to cellSize² |

#### Remove block-based validation

Current validation checks `regionSize % 16 != 0` and `cellSize % 16 != 0`. Remove these—the new units don't need chunk alignment checks.

Keep `regionSize % cellSize != 0` check? No—that doesn't make sense with new units either. Regions are defined in cells, not chunks.

#### New validation

```
CommonConfig {
    if cellSize < 1 or cellSize > 8:
        throw IllegalArgumentException("cellSize must be 1-8 chunks")
    if regionSize < 8 or regionSize > 64:
        throw IllegalArgumentException("regionSize must be 8-64 cells")
    if samplesPerCell < 1 or samplesPerCell > cellSize * cellSize:
        throw IllegalArgumentException("samplesPerCell must be 1 to cellSize²")
}
```

#### Update Spec class

Update ForgeConfigSpec definitions:
- `CELL_SIZE`: range 1-8, default 3, comment "Chunks per side of each cell"
- `REGION_SIZE`: range 8-64, default 32, comment "Cells per side of each region"
- `SAMPLES_PER_CELL`: range 1-64, default 1, comment "Chunks sampled per cell (1 = center only)"

#### Add derived accessors

```
cellBlocks(): int
    return cellSize * 16

regionBlocks(): int
    return regionSize * cellBlocks()

cellsPerRegion(): int
    return regionSize  // Already in cells now
```

Wait—current `cellsPerRegion()` returns `regionSize / cellSize`. With new units:
- regionSize = 32 (cells)
- cellSize = 3 (chunks)

So `cellsPerRegion()` should just return `regionSize` since it's already in cells.

### 2.2 CellPos Block Calculation Update

**File:** `core/CellPos.java`

Change `size()` to use chunk-based config:

```
private static int size():
    return CommonConfig.get().cellBlocks()  // cellSize * 16
```

Or inline it:
```
private static int size():
    return CommonConfig.get().cellSize() * 16
```

The rest of CellPos should work unchanged—it just uses `size()` for block calculations.

### 2.3 RegionPos Block Calculation Update

**File:** `core/RegionPos.java`

Change `size()` to use new config:

```
private static int size():
    return CommonConfig.get().regionBlocks()  // regionSize * cellSize * 16
```

Change `cellsPerRegion()`:
```
private static int cellsPerRegion():
    return CommonConfig.get().regionSize()  // Already in cells
```

### Tests for Configuration

| Test | Setup | Expected |
|------|-------|----------|
| Default cell blocks | Default config | 48 (3 chunks × 16) |
| Default region blocks | Default config | 1536 (32 cells × 3 chunks × 16) |
| Default cells per region | Default config | 32 |
| Cell size range | cellSize = 9 | Throws IllegalArgumentException |
| Region size range | regionSize = 100 | Throws IllegalArgumentException |
| SamplesPerCell range | samplesPerCell = 0 | Throws IllegalArgumentException |
| SamplesPerCell exceeds cell | cellSize = 2, samplesPerCell = 5 | Throws (max is 4) |

---

## Phase 3: Sample Restructure

Implement two-tier sampling where core fields are sampled on cache miss, and source classification fields are sampled lazily.

### 3.1 Two-Tier Field Structure

**File:** `density/Sample.java`

Change from all fields required to nullable source classification fields:

```
record Sample(
    ChunkPos pos,

    // Core fields (always present)
    double continents,
    double depth,

    // Source classification fields (null until enriched)
    Double erosion,
    Double ridges,
    Double temperature,
    Double vegetation
) {
    // Compact constructor for core-only samples
    Sample(ChunkPos pos, double continents, double depth) {
        this(pos, continents, depth, null, null, null, null)
    }
}
```

Note: Using `Double` (boxed) instead of `double` (primitive) to allow null.

### 3.2 Lazy Enrichment Methods

**Add to Sample:**

```
hasFullDensities(): boolean
    return erosion != null
        and ridges != null
        and temperature != null
        and vegetation != null

withFullDensities(sampler: SampleProvider): Sample
    if hasFullDensities():
        return this

    // Sample the additional fields
    fullSample = sampler.sampleFull(pos)
    return new Sample(
        pos,
        continents,
        depth,
        fullSample.erosion(),
        fullSample.ridges(),
        fullSample.temperature(),
        fullSample.vegetation()
    )
```

This requires SampleProvider to have a `sampleFull()` method that returns all fields.

### 3.3 Rename estimatedHeight

Rename `estimatedHeight()` to `estimatedTerrainHeight()` per spec.

### 3.4 Update encode/decode

The `encode()` and `decode()` methods need to handle nullable fields:

```
encode(): CompoundTag
    tag = new CompoundTag()
    tag.putLong("pos", pos.toLong())
    tag.putDouble("continents", continents)
    tag.putDouble("depth", depth)

    if erosion != null:
        tag.putDouble("erosion", erosion)
    if ridges != null:
        tag.putDouble("ridges", ridges)
    if temperature != null:
        tag.putDouble("temperature", temperature)
    if vegetation != null:
        tag.putDouble("vegetation", vegetation)

    return tag

static decode(tag: CompoundTag): Sample
    return new Sample(
        new ChunkPos(tag.getLong("pos")),
        tag.getDouble("continents"),
        tag.getDouble("depth"),
        tag.contains("erosion") ? tag.getDouble("erosion") : null,
        tag.contains("ridges") ? tag.getDouble("ridges") : null,
        tag.contains("temperature") ? tag.getDouble("temperature") : null,
        tag.contains("vegetation") ? tag.getDouble("vegetation") : null
    )
```

### 3.5 SampleCache Two-Tier Sampling

**File:** `density/SampleCache.java`

On cache miss, sample only core fields:

```
getOrCompute(x, z):
    // ... existing cache lookup ...

    // On miss: sample core fields only
    sample = sampler.sampleCore(chunkPos)  // Returns Sample with null classification fields
    cache.put(key, sample)
    return sample
```

### 3.6 SampleProvider Interface Changes

**File:** `density/SampleProvider.java`

The current interface is a functional interface with a single method. Expand it to support two-tier sampling:

**Current:**
```java
@FunctionalInterface
public interface SampleProvider {
    Sample sample(ChunkPos pos);
}
```

**Target:**
```java
public interface SampleProvider {
    Sample sampleCore(ChunkPos pos);   // continents, depth only
    Sample sampleFull(ChunkPos pos);   // all fields
}
```

Remove the `@FunctionalInterface` annotation since the interface now has two methods. Remove the existing `sample()` method entirely — do not keep it as an alias.

**File:** `density/NoiseBasedSampleProvider.java`

Update the implementation to support both methods:

```
sampleCore(pos: ChunkPos): Sample
    continents = sampleNoise(CONTINENTS, pos)
    depth = sampleNoise(DEPTH, pos)
    return new Sample(pos, continents, depth)

sampleFull(pos: ChunkPos): Sample
    continents = sampleNoise(CONTINENTS, pos)
    depth = sampleNoise(DEPTH, pos)
    erosion = sampleNoise(EROSION, pos)
    ridges = sampleNoise(RIDGES, pos)
    temperature = sampleNoise(TEMPERATURE, pos)
    vegetation = sampleNoise(VEGETATION, pos)
    return new Sample(pos, continents, depth, erosion, ridges, temperature, vegetation)
```

Update all call sites that use `sample()` to use `sampleCore()` or `sampleFull()` as appropriate.

### Tests for Sample

| Test | Setup | Expected |
|------|-------|----------|
| Core-only construction | `new Sample(pos, -0.2, 0.5)` | erosion/ridges/temp/veg are null |
| hasFullDensities false | Core-only sample | false |
| hasFullDensities true | Fully enriched sample | true |
| withFullDensities enriches | Core-only sample | Returns sample with all fields |
| withFullDensities idempotent | Already enriched sample | Returns same sample |
| isOcean threshold | continents = -0.18 | true (below -0.17) |
| estimatedTerrainHeight | depth = 0.5 | 142 (70 + 144 × 0.5) |
| encode/decode round-trip core | Core-only sample | Fields preserved, nulls still null |
| encode/decode round-trip full | Full sample | All fields preserved |

---

## Phase 4: Cell Restructure

This is the largest change. The Cell record expands significantly.

### 4.1 New Record Definition

**File:** `cell/Cell.java`

```
record Cell(
    CellPos pos,
    Map<ChunkPos, Sample> samples,  // Keyed by position to handle enrichment
    List<FlowDirection> flowDirections,
    Feature feature,

    // Lazy (computed on first access via withWaypoint)
    // See [[Spatial Infrastructure]] §4.4 for waypoint computation algorithm
    BlockPos waypoint,

    // Set during Elevation Assignment
    Integer entryY,
    Integer exitY,

    // Set during Flow Accumulation
    Integer width,
    Integer depth,
    Integer upstreamCount,
    Integer downstreamCount,

    // Set during Watershed Building
    CellPos terminus,

    // Set during Spline Building
    Double entry_t,
    Double exit_t
) {}
```

Use boxed types (`Integer`, `Double`) for fields that may be null before their phase runs.

### 4.2 Constructors

**Minimal constructor** (used by CellCache on miss):
```
Cell(CellPos pos, Map<ChunkPos, Sample> samples):
    this(pos, samples, List.of(), Feature.NONE,
         null, null, null, null, null, null, null, null, null, null)
```

**From existing record** (for backward compatibility during transition):
```
// REMOVE after refactor complete
Cell(CellPos pos, Sample sample, Feature feature, List<FlowDirection> flowDirections, int y):
    this(pos, Map.of(sample.pos(), sample), flowDirections, feature,
         null, y, y, null, null, null, null, null, null, null)
```

### 4.3 Averaging Methods

All averaging methods iterate over sample values and compute the mean:

```
averageContinents(): double
    return samples.values().stream()
        .mapToDouble(Sample::continents)
        .average()
        .orElse(0.0)

averageDepth(): double
    return samples.values().stream()
        .mapToDouble(Sample::depth)
        .average()
        .orElse(0.0)

averageEstimatedTerrainHeight(): int
    return (int) Math.round(70 + 144 * averageDepth())
```

For source classification fields, require enriched samples:

```
averageErosion(): double
    return samples.values().stream()
        .filter(Sample::hasFullDensities)
        .mapToDouble(s -> s.erosion())
        .average()
        .orElseThrow(() -> new IllegalStateException("No enriched samples"))
```

Same pattern for `averageRidges()`, `averageTemperature()`, `averageVegetation()`.

### 4.4 Computed Properties

```
isOcean(): boolean
    // ALL samples must be ocean
    return samples.values().stream().allMatch(Sample::isOcean)

isBasin(): boolean
    if isOcean():
        return false
    seaLevel = 63  // Or WorldSettings.get().seaLevel() if available
    return samples.values().stream()
        .allMatch(s -> s.estimatedTerrainHeight() < seaLevel)
```

### 4.5 Mutation Methods

Each mutation method returns a new Cell with the updated field(s):

```
withFlowDirections(flowDirections: List<FlowDirection>): Cell
    return new Cell(pos, samples, flowDirections, feature,
        waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount,
        terminus, entry_t, exit_t)

withFeature(feature: Feature): Cell
    return new Cell(pos, samples, flowDirections, feature,
        waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount,
        terminus, entry_t, exit_t)

withWaypoint(waypoint: BlockPos): Cell
    return new Cell(pos, samples, flowDirections, feature,
        waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount,
        terminus, entry_t, exit_t)

withElevations(entryY: int, exitY: int): Cell
    return new Cell(pos, samples, flowDirections, feature,
        waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount,
        terminus, entry_t, exit_t)

withAccumulation(width: int, depth: int, upstreamCount: int, downstreamCount: int): Cell
    return new Cell(pos, samples, flowDirections, feature,
        waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount,
        terminus, entry_t, exit_t)

withWatershed(terminus: CellPos): Cell
    return new Cell(pos, samples, flowDirections, feature,
        waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount,
        terminus, entry_t, exit_t)

withSplineParams(entry_t: double, exit_t: double): Cell
    return new Cell(pos, samples, flowDirections, feature,
        waypoint, entryY, exitY, width, depth, upstreamCount, downstreamCount,
        terminus, entry_t, exit_t)
```

### 4.6 Remove Static Methods

The current `Cell.computeFlowDirections()` and `Cell.hasUpstreamNeighbor()` static methods belong in Flow Evaluation (a lazy service), not on the Cell record. Remove them from Cell.

Flow computation moves to CellCache (lazy) or a dedicated FlowEvaluation service class.

### 4.7 Update encode/decode

The serialization needs significant updates for the new fields. Consider whether all fields need serialization or just the minimal set needed to reconstruct state.

For now, serialize the essential fields:
```
encode(): CompoundTag
    tag = new CompoundTag()
    tag.putLong("pos", pos.toLong())
    tag.putString("feature", feature.name())

    // Samples
    ListTag sampleList = new ListTag()
    for sample in samples:
        sampleList.add(sample.encode())
    tag.put("samples", sampleList)

    // Flow directions
    ListTag flowList = new ListTag()
    for dir in flowDirections:
        flowList.add(StringTag.valueOf(dir.name()))
    tag.put("flowDirections", flowList)

    // Optional fields
    if entryY != null: tag.putInt("entryY", entryY)
    if exitY != null: tag.putInt("exitY", exitY)
    if width != null: tag.putInt("width", width)
    if depth != null: tag.putInt("depth", depth)
    if terminus != null: tag.putLong("terminus", terminus.toLong())

    return tag
```

### Tests for Cell

| Test | Setup | Expected |
|------|-------|----------|
| Minimal construction | `new Cell(pos, samples)` | flowDirections empty, feature NONE, all nullable fields null |
| averageContinents | 3 samples with continents -0.1, -0.2, -0.3 | -0.2 |
| averageEstimatedTerrainHeight | samples with depths averaging 0.5 | 142 |
| isOcean all ocean | 3 samples all below threshold | true |
| isOcean mixed | 2 ocean, 1 land | false |
| isBasin below sea level | all samples < 63, none ocean | true |
| withFlowDirections | cell, add [NORTH, EAST] | new cell with flow directions |
| withElevations | cell, entryY=100, exitY=95 | new cell with elevations set |
| mutation preserves other fields | withFeature on cell with elevations | elevations preserved |

---

## Phase 5: Cache Behavior

Update caches to match spec behavior.

### 5.1 CellCache Multi-Sample Selection

**File:** `cell/CellCache.java`

Implement the greedy farthest-point algorithm for sample position selection:

```
selectSamplePositions(cellSize: int, samplesPerCell: int): List<ChunkPos>
    // Positions are chunk offsets within the cell (0 to cellSize-1)
    center = cellSize / 2
    selected = [(center, center)]

    while selected.size() < samplesPerCell:
        bestPos = null
        bestMinDist = -1

        for x in 0 until cellSize:
            for z in 0 until cellSize:
                if (x, z) in selected:
                    continue

                minDist = min(distance((x, z), s) for s in selected)
                if minDist > bestMinDist:
                    bestMinDist = minDist
                    bestPos = (x, z)

        selected.add(bestPos)

    return selected

distance(a, b): double
    dx = a.x - b.x
    dz = a.z - b.z
    return sqrt(dx*dx + dz*dz)
```

Cache sample positions per cell size (they're deterministic):
```
private static Map<Integer, List<Point>> samplePositionCache = new HashMap<>()

getSamplePositions(cellSize: int, samplesPerCell: int): List<Point>
    key = cellSize * 100 + samplesPerCell  // Simple composite key
    return samplePositionCache.computeIfAbsent(key,
        k -> selectSamplePositions(cellSize, samplesPerCell))
```

### 5.2 CellCache Lazy Flow Computation

Remove flow direction computation from `getOrCompute()`. Cells should be created with empty flow directions:

```
getOrCompute(pos: CellPos): Cell
    // ... cache lookup ...

    // On miss: create cell with samples only
    samples = collectSamples(pos)
    cell = new Cell(pos, samples)  // No flow directions yet
    cache.put(key, cell)
    return cell

collectSamples(pos: CellPos): Map<ChunkPos, Sample>
    positions = getSamplePositions(config.cellSize(), config.samplesPerCell())
    samples = new HashMap<>()

    cellMinChunkX = pos.x() * config.cellSize()
    cellMinChunkZ = pos.z() * config.cellSize()

    for offset in positions:
        chunkX = cellMinChunkX + offset.x
        chunkZ = cellMinChunkZ + offset.z
        chunkPos = new ChunkPos(chunkX, chunkZ)
        sample = SampleCache.get().getOrCompute(chunkX * 16, chunkZ * 16)
        samples.put(chunkPos, sample)

    return samples
```

Flow directions are computed lazily when first requested. Add a method:

```
getOrComputeWithFlow(pos: CellPos): Cell
    cell = getOrCompute(pos)
    if not cell.flowDirections().isEmpty():
        return cell

    // Skip flow computation for regions that don't need it
    regionType = RegionTypeCache.get().getOrCompute(pos.getRegion())
    if regionType == OCEANIC or regionType == INLAND:
        return cell  // flow stays empty

    // Compute flow directions
    flowDirections = computeFlowDirections(cell)
    enriched = cell.withFlowDirections(flowDirections)
    put(enriched)
    return enriched

computeFlowDirections(cell: Cell): List<FlowDirection>
    thisHeight = cell.averageEstimatedTerrainHeight()
    slopes = []

    for dir in FlowDirection.D8:
        neighbor = getOrCompute(cell.pos().relative(dir))
        neighborHeight = neighbor.averageEstimatedTerrainHeight()

        if neighborHeight < thisHeight:
            slope = thisHeight - neighborHeight
            slopes.add((dir, slope))

    // Sort by steepest first
    slopes.sortByDescending(entry -> entry.slope)
    return slopes.map(entry -> entry.dir)
```

**Thread Safety Note:** The `getOrComputeWithFlow()` pattern (get → check → compute → put) is a check-then-act race condition with the synchronized map. Two threads could both compute flow for the same cell concurrently. This is wasteful but not incorrect — flow computation is deterministic, so both threads will produce identical results. The second put simply overwrites with the same value. If profiling shows this is a performance problem, a more sophisticated locking strategy can be added later.

### 5.3 CellCache Remove Classification

Remove the `Feature.classify()` call from `getOrCompute()`. Classification happens in Feature Classification, not during cache population.

Current code:
```java
Cell value = Feature.classify(new Cell(pos, sample, Feature.DEFAULT, flowDirections, y), null);
```

Replace with:
```java
Cell value = new Cell(pos, samples);
```

### 5.4 RegionCache Remove Path Computation

**File:** `region/RegionCache.java`

If `computePaths()` or similar is being called during cache miss, remove it. RegionCache should only:
1. Create the Region skeleton
2. Classify via Region Classification
3. Identify boundaries for COASTAL regions

Path computation happens in Flowline Tracing, not during region caching.

### Tests for Cache Behavior

| Test | Setup | Expected |
|------|-------|----------|
| Sample positions N=1 | cellSize=3, samplesPerCell=1 | [(1,1)] (center only) |
| Sample positions N=5 | cellSize=3, samplesPerCell=5 | center + 4 corners |
| Sample positions N=9 | cellSize=3, samplesPerCell=9 | all 9 positions |
| CellCache miss creates basic cell | New position | Cell with samples, empty flowDirections, NONE feature |
| CellCache getOrComputeWithFlow | Cell without flow | Returns cell with flow directions, updates cache |
| CellCache no classification | Cache miss | feature is NONE (not computed) |

---

## Command Updates

The diagnostic commands use the old Cell and Sample APIs. Update them as part of this implementation.

### SampleCommand.java

| Line | Current | Target |
|------|---------|--------|
| ~114 | `cell.sample()` | `cell.samples().iterator().next()` (or display all samples) |
| ~118 | `sample.estimatedHeight()` | `sample.estimatedTerrainHeight()` |
| ~119 | `cell.y()` | `cell.entryY()` and `cell.exitY()` (display both, handle null) |

Consider updating the command output to show:
- Sample count (e.g., "3 samples")
- Average values via `cell.averageContinents()`, `cell.averageDepth()`, etc.
- Individual sample details if verbose flag is set

### DebugCommand.java

| Line | Current | Target |
|------|---------|--------|
| ~52 | `cell.y()` | `cell.entryY()` (or `cell.averageEstimatedTerrainHeight()` for display) |

### Null Handling

The new Cell fields (`entryY`, `exitY`, `width`, `depth`, etc.) are nullable until their respective pipeline phases run. Commands should display "not computed" or similar rather than crashing on null.

```
// Example pattern
if cell.entryY() != null:
    display "Entry Y: " + cell.entryY()
else:
    display "Entry Y: (not computed)"
```

---

## Validation Checklist

### Unit Tests Required

From the Level 4 spec, implement these test cases:

**Coordinate Types:**
- [ ] CellPos round-trip (create → BlockPos → back)
- [ ] CellPos packing (create → long → unpack)
- [ ] RegionPos containment (iterate cells, verify count)
- [ ] Negative coordinate floor division
- [ ] Border cells NORTH (correct count, same Z)
- [ ] Border cells for all cardinal directions

**FlowDirection:**
- [ ] opposite() for all directions
- [ ] D8 contains 8 directions, excludes NONE
- [ ] rotateToward (clockwise, counter-clockwise, same, opposite)
- [ ] toward (diagonal, cardinal, same position)

**Sample:**
- [ ] Ocean detection at threshold
- [ ] Two-tier enrichment
- [ ] estimatedTerrainHeight calculation
- [ ] encode/decode round-trip

**Cell:**
- [ ] Multi-sample averaging
- [ ] isOcean (all ocean vs. mixed)
- [ ] isBasin detection
- [ ] Mutation method preservation

**Caches:**
- [ ] Sample position selection for various N values
- [ ] CellCache lazy flow behavior
- [ ] Cache hit/miss metrics

### Integration Points

After this implementation is complete:
- [ ] Region Discovery can query RegionCache for classified regions
- [ ] Feature Classification can request cells with flow via CellCache
- [ ] Flowline Tracing can read flow directions from enriched cells
- [ ] Downstream specs can use Cell mutation methods to enrich cells

### Diagnostic Commands

Verify these commands work with the new implementation:

| Command | What to Check |
|---------|---------------|
| `/rivertale sample <x> <z>` | Shows core fields; shows "not enriched" for classification fields if not sampled |
| `/rivertale cell <x> <z>` | Shows sample count, average values, flow directions (if computed) |
| `/rivertale metrics` | Cache hit ratios, sample counts |

---

## Visualization Updates

### Update /rivertale debug Output

**File:** `command/DebugCommand.java`

After the full refactor, `/rivertale debug` should show all Cell fields:

```
=== River Debug at X, Z ===
Cell: CellPos(x, z) (FEATURE_NAME)
Flow: NORTHEAST
entryY: 72, exitY: 70
width: 8, depth: 4
upstreamCount: 2, downstreamCount: 1
terminus: CellPos(tx, tz)
Watershed: (terminus info or "none")
```

### Update /rivertale cell Output

The `/rivertale cell` command should also show the new fields:

```
=== Cell at CellPos(x, z) ===
Samples: 9 (3x3 grid)
Average depth: 0.42
Average continents: 0.81
Feature: RUN
Flow directions: [NORTHEAST, EAST] (sorted by steepness)
entryY: 72, exitY: 70
width: 8, depth: 4
upstreamCount: 2, downstreamCount: 1
terminus: CellPos(tx, tz)
```

---

## Migration Notes

### Breaking Changes

This refactor breaks:
1. **All code that constructs Cell directly** — signature changed completely
2. **All code that reads `cell.sample()`** — now `cell.samples()` (plural)
3. **All code that reads `cell.y()`** — now `cell.entryY()` and `cell.exitY()`
4. **All code that calls `Cell.computeFlowDirections()`** — moved to CellCache
5. **Configuration file values** — blocks → chunks/cells

### Backward Compatibility

None. This is a clean break. Delete any existing `rivertale-common.toml` config file — the old block-based values are out of range for the new chunk/cell-based units.

### Recommended Approach

1. Create a feature branch
2. Work through phases in order
3. Run tests after each phase
4. Don't merge until all phases complete and tests pass
