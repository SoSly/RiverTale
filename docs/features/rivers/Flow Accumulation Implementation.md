---
level: 5
parent: "[[Flow Accumulation]]"
status: draft
---

# Flow Accumulation — Implementation

This document provides implementation guidance for the [[Flow Accumulation]] specification. The spec is the source of truth; this document describes how to refactor the existing exploratory code to match.

## Scope

This implementation covers:
- Adding width, depth, upstreamCount, downstreamCount fields to Cell
- Adding configuration parameters for saturation formulas
- Implementing `determineAccumulation()` pipeline step
- Implementing width formula (logarithmic saturation + seeded variance)
- Implementing depth formula (logarithmic saturation from confluences)

This does NOT cover:
- Cell field definitions (see [[Spatial Infrastructure Implementation]])
- Elevation assignment (see [[Elevation Assignment Implementation]])

## Overview

Flow Accumulation is largely unimplemented. The existing `accumulation()` method counts upstream cells for diagonal crossing resolution but doesn't compute width/depth or store results on Cell:

| Area | Current State | Target State |
|------|---------------|--------------|
| Cell.width | Missing | Add field |
| Cell.depth | Missing | Add field |
| Cell.upstreamCount | Missing | Add field |
| Cell.downstreamCount | Missing | Add field |
| Width formula | Not implemented | Logarithmic saturation + variance |
| Depth formula | Not implemented | Logarithmic saturation from confluences |
| Pipeline step | Not present | `determineAccumulation()` method |
| Config parameters | Missing | 7 new parameters |

## Definition of Done

**What "done" means:**

- Cell has width, depth, upstreamCount, downstreamCount fields
- `Cell.withAccumulation()` mutation method exists
- Config has all 7 parameters with spec defaults
- `determineAccumulation()` is public and called by RiverShaping
- Width uses logarithmic saturation with seeded variance
- Depth uses logarithmic saturation from confluence count
- Unit tests from spec pass

**What "done" does NOT mean:**

- Feature classification based on width/depth (that's Feature Classification)
- Terrain carving based on depth (that's Terrain Shaping)

## Implementation Sequence

```
Phase 1: Cell Fields
    ├── Add width, depth, upstreamCount, downstreamCount (Spatial Infrastructure)
    └── Add withAccumulation() mutation method

Phase 2: Configuration
    └── Add 7 parameters to CommonConfig

Phase 3: Algorithm
    ├── Create determineAccumulation() method
    ├── Implement recursive accumulation counting with memoization
    ├── Implement width formula
    ├── Implement depth formula
    └── Implement seeded variance
```

---

## Phase 1: Cell Fields

### 1.1 Add Fields

**Defined in:** [[Spatial Infrastructure Implementation]]

The Cell record needs:
```
record Cell(
    // ... existing fields ...
    width: int,           // river width in blocks
    depth: int,           // channel depth in blocks
    upstreamCount: int,   // immediate upstream neighbors (0 for source, 2+ for confluence)
    downstreamCount: int  // immediate downstream neighbors (0 for terminus, 1 otherwise)
)
```

### 1.2 Mutation Method

**File:** `cell/Cell.java`

```
withAccumulation(width: int, depth: int, upstreamCount: int, downstreamCount: int): Cell
    return new Cell(..., width, depth, upstreamCount, downstreamCount)
```

---

## Phase 2: Configuration

### 2.1 Add Parameters

**File:** `config/CommonConfig.java`

Add the following parameters:

| Parameter | Type | Default | Range | Description |
|-----------|------|---------|-------|-------------|
| minWidth | int | 3 | 1-16 | Minimum river width (smallest streams) |
| maxWidth | int | 24 | 8-47 | Maximum river width (must be < cellSize) |
| widthScale | double | 3.0 | 0.5-10.0 | Logarithmic width growth multiplier |
| variancePercent | double | 0.2 | 0.0-0.5 | Width variance fraction (±20% default) |
| minDepth | int | 2 | 1-8 | Minimum channel depth |
| maxDepth | int | 8 | 4-16 | Maximum channel depth |
| depthScale | double | 2.0 | 0.5-5.0 | Logarithmic depth growth multiplier |

### 2.2 Validation

maxWidth must be less than cell size in blocks. Add validation in config loading:
```
if maxWidth >= config.cellBlocks():
    warn and clamp to cellBlocks() - 1
```

Note: `cellSize` is in chunks (default 3), so use `cellBlocks()` which returns `cellSize * 16` (default 48 blocks).

---

## Phase 3: Algorithm

### 3.1 Create determineAccumulation Method

**File:** `river/Watershed.java`

```
public void determineAccumulation():
    record = Store.getTimer(Watershed.class, "determineAccumulation").start()

    memo = new Map<CellPos, AccumulationResult>()

    for cellPos in allCells():
        computeAccumulation(cellPos, memo)

    record.stop()
```

### 3.2 AccumulationResult Record

**New file or inner class:** `river/AccumulationResult.java`

```
record AccumulationResult(
    int cellCount,        // total cells upstream including self
    int confluenceCount   // total confluences upstream including self if confluence
)
```

### 3.3 Recursive Accumulation Counting

```
private computeAccumulation(cellPos: CellPos, memo: Map<CellPos, AccumulationResult>): AccumulationResult
    if memo.containsKey(cellPos):
        return memo.get(cellPos)

    cell = CellCache.get().getOrCompute(cellPos)
    upstreamSet = upstream(cellPos)
    downstreamPos = downstream(cellPos)

    // Count immediate neighbors
    upstreamCount = upstreamSet.size()
    downstreamCount = downstreamPos == null ? 0 : 1

    // Recursively count all upstream
    totalCells = 1  // count self
    totalConfluences = upstreamCount >= 2 ? 1 : 0  // self is confluence if 2+ upstream

    for upstreamPos in upstreamSet:
        upstreamResult = computeAccumulation(upstreamPos, memo)
        totalCells += upstreamResult.cellCount()
        totalConfluences += upstreamResult.confluenceCount()

    // Apply saturation formulas
    width = computeWidth(totalCells, cellPos)
    depth = computeDepth(totalConfluences)

    // Store results on Cell
    CellCache.get().put(cell.withAccumulation(width, depth, upstreamCount, downstreamCount))

    result = new AccumulationResult(totalCells, totalConfluences)
    memo.put(cellPos, result)
    return result
```

### 3.4 Width Formula

```
private computeWidth(cellCount: int, cellPos: CellPos): int
    config = CommonConfig.get()

    // Logarithmic saturation
    baseWidth = config.minWidth() + Math.log(cellCount) * config.widthScale()

    // Clamp to bounds
    clampedWidth = clamp(baseWidth, config.minWidth(), config.maxWidth())

    // Apply seeded variance (±variancePercent)
    variance = seededRandom(cellPos) * 2 - 1  // range [-1, 1]
    adjustedWidth = clampedWidth * (1 + variance * config.variancePercent())

    // Final clamp after variance
    return clamp(Math.round(adjustedWidth), config.minWidth(), config.maxWidth())
```

### 3.5 Depth Formula

```
private computeDepth(confluenceCount: int): int
    config = CommonConfig.get()

    if confluenceCount == 0:
        return config.minDepth()

    // Logarithmic saturation
    baseDepth = config.minDepth() + Math.log(confluenceCount) * config.depthScale()

    // Clamp to bounds
    return clamp(Math.round(baseDepth), config.minDepth(), config.maxDepth())
```

### 3.6 Seeded Random

```
private seededRandom(cellPos: CellPos): double
    worldSeed = WorldSettings.get().seed()

    // Combine seed and position into deterministic hash
    hash = mixHash(worldSeed, cellPos.x(), cellPos.z())
    return hashToFloat(hash)  // returns 0.0 to 1.0

private mixHash(seed: long, x: int, z: int): long
    // Standard hash mixing - example using FNV or similar
    h = seed
    h ^= x
    h *= 0x517cc1b727220a95L
    h ^= z
    h *= 0x517cc1b727220a95L
    return h

private hashToFloat(hash: long): double
    // Convert to 0.0-1.0 range
    return (hash & 0x7FFFFFFFFFFFFFFFL) / (double) Long.MAX_VALUE
```

Same seed + same cell = same random value, ensuring deterministic generation.

---

## Relationship to Existing accumulation() Method

### 3.7 Keep Existing Method

**File:** `river/Watershed.java` lines 301-318

The existing `accumulation()` method counts upstream cells for diagonal crossing resolution during Watershed Building. It should remain for that purpose.

The new `determineAccumulation()` is a separate pipeline step that:
1. Counts cells AND confluences
2. Computes width and depth
3. Stores results on Cell

The existing method is used during building; the new method is called after validation.

---

## Wiring into RiverShaping

This section describes how Flow Accumulation integrates into the `RiverShaping.shape()` pipeline. Flow Accumulation computes width/depth and neighbor counts for each cell. This is the last stage before Reclassification.

### Current State (after Elevation Assignment)

```
public static void shape(ChunkAccess chunk, int seaLevel):
    // Region Discovery
    workingSet = RegionExplorer.discover(...)
    if workingSet.isEmpty():
        return

    // Boundary Identification
    for region in workingSet:
        // ... boundary detection ...

    // Feature Classification (early phase)
    for region in workingSet:
        // ... classify cells ...

    // Flowline Tracing
    tracer = new FlowlineTracer(workingSet)
    // ... trace and group flowlines ...
    flowlineGroups = groupByTerminus(allFlowlines)

    // Watershed Building
    watersheds = new List<Watershed>()
    for (terminus, flowlines) in flowlineGroups:
        watershed = new Watershed(terminus, flowlines)
        watersheds.add(watershed)

    // Watershed Validation
    validWatersheds = new List<Watershed>()
    for watershed in watersheds:
        watershed.validate()
        if watershed.allCells().isEmpty():
            continue
        WatershedCache.get().put(watershed)
        validWatersheds.add(watershed)

    // Elevation Assignment
    for watershed in validWatersheds:
        watershed.assignElevations()
```

### This Implementation Adds

**Flow Accumulation** — After elevation assignment. Computes width, depth, and neighbor counts for each cell.

```
public static void shape(ChunkAccess chunk, int seaLevel):
    // ... Region Discovery, Boundary Identification, Feature Classification, Flowline Tracing, Watershed Building, Validation, Elevation unchanged ...

    // Flow Accumulation — NEW
    for watershed in validWatersheds:
        watershed.determineAccumulation()

    // Reclassification (from Feature Classification Implementation)
    for watershed in validWatersheds:
        for cellPos in watershed.allCells():
            cell = CellCache.get().getOrCompute(cellPos)
            reclassified = Feature.classify(cell, watershed)
            CellCache.get().put(reclassified)

    // ... Spline Building, Terrain Shaping, Water Placement follow ...
```

**Note:** After Flow Accumulation, Reclassification runs. Reclassification was added as a placeholder in Feature Classification Implementation; now it has the data it needs (watershed context, elevation, accumulation).

### Validation at This Stage

After this implementation, you can verify:
- Source cells have upstreamCount = 0
- Terminus cells have downstreamCount = 0
- Confluence cells have upstreamCount >= 2
- Width grows logarithmically with upstream cell count
- Depth grows logarithmically with upstream confluence count
- Seeded variance produces deterministic width adjustments

After Reclassification (which follows immediately):
- RUN cells identified (normal flow segments)
- WATERFALL/CASCADE cells identified (steep drop segments)
- CONFLUENCE cells identified (upstreamCount >= 2)
- MOUTH cells identified (at terminus)

---

## Validation Checklist

### Unit Tests Required

From the Level 4 spec:

| Test | Setup | Expected |
|------|-------|----------|
| Source cell | No upstream | width = minWidth, depth = minDepth, upstreamCount = 0 |
| Linear path | 5 cells in sequence | cellCount increments downstream |
| Simple confluence | Two tributaries meeting | upstreamCount = 2, cellCount = sum + 1 |
| Nested confluences | Confluence feeding confluence | confluenceCount increments correctly |
| Width saturation | 1000 upstream cells | Width clamped at maxWidth |
| Depth from confluences | 10 confluences upstream | Depth reflects log(10) * depthScale |
| Variance determinism | Same cell, same seed | Same width every time |
| Terminus | downstream = null | downstreamCount = 0 |

### Integration Points

After this implementation:
- Feature Classification can use upstreamCount to identify confluences
- Spline Building can use width for river geometry
- Terrain Shaping can use depth for channel carving

---

## Visualization Updates

### Update ClientRegionCache.Cell

**File:** `client/ClientRegionCache.java`

Add width and depth to the client-side Cell record:

```
record Cell(
    CellPos pos,
    Feature feature,
    FlowDirection flow,
    int entryY,
    int exitY,
    int width,      // new
    int depth       // new
)
```

### Update renderFlowArrows()

**File:** `client/RegionRenderer.java`

Scale arrow thickness by width to show river size:

```
renderFlowArrows(cell):
    thickness = map(cell.width(), minWidth, maxWidth, 1.0, 3.0)
    drawArrow(cell.pos(), cell.flow(), thickness, cell.feature().color())
```

Wider rivers get thicker arrows.

### Add /rivertale vis accumulation

Toggle width/depth visualization:
- Arrow thickness shows width
- Optional: color gradient shows depth

---

## Migration Notes

### Breaking Changes

- Cell gains 4 new fields (width, depth, upstreamCount, downstreamCount)
- New config parameters (7 total)

### Dependencies

Requires from Spatial Infrastructure:
- Cell.width, Cell.depth, Cell.upstreamCount, Cell.downstreamCount fields
- Cell.withAccumulation() mutation method

Requires from Watershed Building:
- Validated watershed with upstream/downstream links

### Timer

Add timer:
```
Store.getTimer(Watershed.class, "determineAccumulation")
```
