---
level: 5
parent: "[[Elevation Assignment]]"
status: draft
---

# Elevation Assignment — Implementation

This document provides implementation guidance for the [[Elevation Assignment]] specification. The spec is the source of truth; this document describes how to refactor the existing exploratory code to match.

## Scope

This implementation covers:
- Changing Cell from single `y` to `entryY`/`exitY` boundary model
- Extracting `assignYLevels()` from Watershed constructor to public method
- Removing sea level clamping from downstream pass
- Implementing exit point sampling (waypoint toward downstream)
- Implementing confluence handling with tributary adjustment
- Fixing upstream pass to set terminus to seaLevel - 1

This does NOT cover:
- Cell field definitions (see [[Spatial Infrastructure Implementation]])
- Flow accumulation (see [[Flow Accumulation Implementation]])

## Overview

The basic two-pass pattern (downstream then upstream) is correct, but the data model and algorithm details diverge significantly:

| Area | Current State | Target State |
|------|---------------|--------------|
| Cell elevation | Single `y` field | `entryY` and `exitY` fields |
| Terrain sampling | Cell center (`cell.sample()`) | Exit point toward downstream |
| Downstream pass | Clamps to seaLevel (lines 239, 246) | No sea level clamp |
| Upstream pass | Raises based on minSlope from downstream | Sets terminus to seaLevel - 1, propagates up |
| Confluences | Constrains current cell from upstream | Takes min exitY, adjusts higher tributaries |
| Mutation | `cell.withY(newY)` | `cell.withElevations(entryY, exitY)` |

## Definition of Done

**What "done" means:**

- Cell has `entryY` and `exitY` fields with `withElevations()` mutation
- `assignElevations()` is public and called by RiverShaping
- Downstream pass follows terrain without sea level clamp
- Exit point estimated via waypoint toward downstream
- Confluences: min exitY wins, higher tributaries adjusted
- Upstream pass sets terminus to seaLevel - 1, propagates
- Adjacent cells satisfy: `upstream.exitY == downstream.entryY`
- Unit tests from spec pass

**What "done" does NOT mean:**

- Watershed constructor calls assignElevations (removed by Watershed Building)
- Feature classification of steep segments (that's Feature Classification)

## Implementation Sequence

```
Phase 1: Data Model
    ├── Add entryY and exitY to Cell (Spatial Infrastructure)
    ├── Add withElevations() mutation method
    └── Remove withY() method

Phase 2: Extract Method
    └── Rename assignYLevels() to assignElevations() and make public

Phase 3: Exit Point Sampling
    └── Implement estimateExitPoint() using waypoint

Phase 4: Downstream Pass Rewrite
    ├── Remove sea level clamping
    ├── Add exit point sampling
    └── Implement confluence handling with tributary adjustment

Phase 5: Upstream Pass Rewrite
    └── Set terminus to seaLevel - 1, then propagate
```

---

## Phase 1: Data Model

### 1.1 Cell Fields

**Defined in:** [[Spatial Infrastructure Implementation]]

The Cell record needs:
```
record Cell(
    // ... existing fields ...
    entryY: int,    // elevation where water enters cell
    exitY: int      // elevation where water exits cell
)
```

### 1.2 Mutation Method

**File:** `cell/Cell.java`

**Replace:**
```
withY(newY: int): Cell
```

**With:**
```
withElevations(entryY: int, exitY: int): Cell
    return new Cell(..., entryY, exitY)
```

### 1.3 Constraint

Adjacent cells must satisfy: `upstream.exitY == downstream.entryY`

This is enforced by the algorithm, not validated after the fact.

---

## Phase 2: Extract Method

### 2.1 Rename and Make Public

**File:** `river/Watershed.java`

**Current (line 204):**
```
private void assignYLevels()
```

**Target:**
```
public void assignElevations()
```

**Note:** The constructor call is already removed by [[Watershed Building Implementation]]. This doc handles the method transformation.

---

## Phase 3: Exit Point Sampling

### 3.1 Implement Exit Point Estimation

**Add to:** `river/Watershed.java`

```
estimateExitPoint(current: CellPos, downstream: CellPos): BlockPos
    cell = CellCache.get().getOrCompute(current)
    waypoint = cell.waypoint()
    direction = FlowDirection.toward(current, downstream)

    // Project waypoint toward cell boundary in downstream direction
    cellSize = CommonConfig.get().cellSize()
    halfCell = cellSize / 2

    exitX = waypoint.x() + direction.dx * halfCell
    exitZ = waypoint.z() + direction.dz * halfCell

    return BlockPos(exitX, 0, exitZ)  // Y is what we're computing
```

### 3.2 Dependency

Requires from Spatial Infrastructure:
- `Cell.waypoint()` — center point with noise offset
- `FlowDirection.toward(CellPos, CellPos)` — direction between cells

---

## Phase 4: Downstream Pass Rewrite

### 4.1 Remove Sea Level Clamping

**Current (lines 239, 246):**
```
cache.put(cell.withY(Math.max(seaLevel, terrainY)));
// and
int constrainedY = Math.max(seaLevel, Math.min(terrainY, maxAllowedY));
```

**Problem:** Sea level clamping during downstream pass prevents rivers from following terrain into depressions. The spec says to follow terrain freely, then normalize in the upstream pass.

**Target:** Remove `Math.max(seaLevel, ...)` from downstream pass entirely.

### 4.2 Rewrite Downstream Pass

**Replace `assignYLevelsDownstream()` with:**

```
assignDownstream(watershed):
    sources = findSources()
    visited = new Set<CellPos>()
    minSlope = CommonConfig.get().minSlope()

    for source in sources:
        assignDownstreamFromSource(source, visited, minSlope)

assignDownstreamFromSource(source, visited, minSlope):
    queue = new Queue<CellPos>()
    queue.add(source)

    while not queue.isEmpty():
        current = queue.poll()

        if visited.contains(current):
            continue
        visited.add(current)

        cell = CellCache.get().getOrCompute(current)
        upstreamSet = upstream(current)
        downstreamPos = downstream(current)

        // Compute entryY
        if upstreamSet.isEmpty():
            // Source: use terrain at cell
            entryY = cell.averageEstimatedTerrainHeight()
        else:
            // Non-source: get from upstream exitY (handles confluence)
            entryY = getUpstreamExitY(upstreamSet)

        // Compute exitY
        if downstreamPos == null:
            // Terminus: drop by minSlope (will normalize if below sea level)
            exitY = entryY - minSlope
        else:
            exitPoint = estimateExitPoint(current, downstreamPos)
            terrainAtExit = SampleCache.get().getOrCompute(exitPoint).estimatedTerrainHeight()
            exitY = min(entryY - minSlope, terrainAtExit)

        // Store
        CellCache.get().put(cell.withElevations(entryY, exitY))

        // Continue downstream
        if downstreamPos != null:
            queue.add(downstreamPos)
```

### 4.3 Confluence Handling

**Add method:**

```
getUpstreamExitY(upstreamSet: Set<CellPos>): int
    // Find minimum exitY among all upstream cells
    minExitY = MAX_INT
    for upstreamPos in upstreamSet:
        upstreamCell = CellCache.get().getOrCompute(upstreamPos)
        minExitY = min(minExitY, upstreamCell.exitY())

    // Adjust any tributaries with higher exitY to match
    for upstreamPos in upstreamSet:
        upstreamCell = CellCache.get().getOrCompute(upstreamPos)
        if upstreamCell.exitY() > minExitY:
            adjusted = upstreamCell.withElevations(upstreamCell.entryY(), minExitY)
            CellCache.get().put(adjusted)

    return minExitY
```

**Current behavior (lines 241-246):** Constrains current cell based on upstream values, but doesn't adjust upstream cells.

**Target behavior:** Take minimum exitY, then adjust higher tributaries to match. This creates steeper drops in those tributaries (entryY unchanged, exitY lowered).

---

## Phase 5: Upstream Pass Rewrite

### 5.1 Set Terminus Explicitly

**Current (lines 254-278):** Walks upstream and raises cells based on minSlope from downstream, but doesn't explicitly set terminus to seaLevel - 1.

**Target:**

```
normalizeFromTerminus(watershed):
    terminus = findTerminus()
    terminusCell = CellCache.get().getOrCompute(terminus)
    waterLevel = WorldSettings.get().seaLevel() - 1
    minSlope = CommonConfig.get().minSlope()

    // Check if normalization needed
    if terminusCell.exitY() >= waterLevel:
        return

    // Raise terminus to water level
    newExitY = waterLevel
    newEntryY = max(terminusCell.entryY(), newExitY + minSlope)
    CellCache.get().put(terminusCell.withElevations(newEntryY, newExitY))

    // Propagate upstream
    propagateUpstream(terminus, minSlope)

propagateUpstream(start, minSlope):
    stack = new Stack<CellPos>()
    for upstreamPos in upstream(start):
        stack.push(upstreamPos)

    while not stack.isEmpty():
        current = stack.pop()
        cell = CellCache.get().getOrCompute(current)
        downstreamPos = downstream(current)
        downstreamCell = CellCache.get().getOrCompute(downstreamPos)

        // Our exitY must equal downstream's entryY
        requiredExitY = downstreamCell.entryY()

        if cell.exitY() < requiredExitY:
            newExitY = requiredExitY
            newEntryY = max(cell.entryY(), newExitY + minSlope)
            CellCache.get().put(cell.withElevations(newEntryY, newExitY))

        // Continue upstream
        for upstreamPos in upstream(current):
            stack.push(upstreamPos)
```

### 5.2 Main Entry Point

```
public void assignElevations():
    record = Store.getTimer(Watershed.class, "assignElevations").start()

    assignDownstream(this)
    normalizeFromTerminus(this)

    record.stop()
```

---

## Wiring into RiverShaping

This section describes how Elevation Assignment integrates into the `RiverShaping.shape()` pipeline. Elevation Assignment computes entryY/exitY for each cell.

### Current State (after Watershed Validation)

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
```

### This Implementation Adds

**Elevation Assignment** — After validation. Assigns entryY/exitY to each cell via two-pass algorithm.

```
public static void shape(ChunkAccess chunk, int seaLevel):
    // ... Region Discovery, Boundary Identification, Feature Classification, Flowline Tracing, Watershed Building, Validation unchanged ...

    // Elevation Assignment — NEW
    for watershed in validWatersheds:
        watershed.assignElevations()

    // ... Flow Accumulation, Reclassification follow ...
```

**Note:** Elevation Assignment and Flow Accumulation have no dependency on each other. They could theoretically run in parallel, but sequential execution is simpler and the performance difference is negligible.

### Validation at This Stage

After this implementation, you can verify:
- Source cells have entryY from terrain sampling
- Each cell satisfies: `upstream.exitY == downstream.entryY`
- Terminus cells have exitY at seaLevel - 1 (after normalization)
- Confluences: all tributaries have same exitY at join point
- Downhill flow: `entryY >= exitY` for each cell

You **cannot** yet verify:
- Cells have width/depth data (that's Flow Accumulation)
- Final feature classification (that's Reclassification)
- Steep segments classified as WATERFALL/CASCADE (that's Reclassification)

---

## Validation Checklist

### Unit Tests Required

From the Level 4 spec:

| Test | Setup | Expected |
|------|-------|----------|
| Simple linear path | 5 cells, terrain descending | Each exitY follows terrain, drops at least minSlope |
| Steep terrain | Terrain drops 10/cell, minSlope=1 | exitY follows terrain (drops 10) |
| Flat terrain | Terrain constant, minSlope=1 | exitY = entryY - minSlope each cell |
| Below sea level | Path goes 20 below | Upstream pass raises to seaLevel - 1 |
| Confluence | Two tributaries meeting | Both have same exitY at confluence |
| Higher tributary adjusted | Confluence with mismatched exitY | Higher tributary's exitY lowered |
| Single-cell | Only terminus | entryY from terrain, exitY at water level |

### Integration Points

After this implementation:
- Flow Accumulation can run (no elevation dependency)
- Spline Building can use entryY/exitY for profile curves
- Feature Classification can detect steep segments (large entryY - exitY)

---

## Visualization Updates

### Update ClientRegionCache.Cell

**File:** `client/ClientRegionCache.java`

Update the client-side Cell record to include both elevations:

```
// Before
record Cell(CellPos pos, Feature feature, FlowDirection flow, int y)

// After
record Cell(CellPos pos, Feature feature, FlowDirection flow, int entryY, int exitY)
```

### Update renderCellBorders()

**File:** `client/RegionRenderer.java`

Update cell border rendering to use exitY for line height (where water exits the cell):

```
renderCellBorders(cell):
    // Use exitY for visualization (downstream edge of cell)
    y = cell.exitY()
    drawCellBorder(cell.pos(), y, cell.feature().color())
```

Optionally show elevation gradient by drawing entry edge at entryY and exit edge at exitY.

---

## Migration Notes

### Breaking Changes

- `Cell.y()` removed, use `Cell.entryY()` and `Cell.exitY()`
- `Cell.withY()` removed, use `Cell.withElevations()`
- `assignYLevels()` renamed to `assignElevations()` and made public
- Elevation values change due to algorithm fixes (no sea level clamp, proper confluence handling)

### Dependencies

Requires from Spatial Infrastructure:
- `Cell.entryY()` and `Cell.exitY()` fields
- `Cell.withElevations(entryY, exitY)` mutation
- `Cell.waypoint()` for exit point estimation
- `Cell.averageEstimatedTerrainHeight()` for source entryY

Requires from Watershed Building:
- Constructor no longer calls assignYLevels()

### Timer Updates

Rename timer:
- `Store.getTimer(Watershed.class, "assignYLevels")` → `Store.getTimer(Watershed.class, "assignElevations")`
