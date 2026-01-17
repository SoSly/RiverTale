---
level: 5
parent: "[[Region Discovery]]"
status: draft
---

# Region Discovery — Implementation

This document provides implementation guidance for the [[Region Discovery]] specification. The spec is the source of truth; this document describes how to refactor the existing exploratory code to match.

## Scope

This implementation covers:
- Creating the `RegionExplorer` class
- Refactoring `RiverShaping` to use it
- Fixing the D8→D4 neighbor traversal
- Adding border cell flow direction checks

This does NOT cover:
- Region classification logic (see [[Spatial Infrastructure Implementation]])
- Boundary identification (see [[Boundary Identification Implementation]])
- Flowline tracing (see [[Flowline Tracing Implementation]])

## Overview

The current code works but diverges from the spec in structure and algorithm:

| Area | Current State | Target State |
|------|---------------|--------------|
| Entry point | `RiverShaping` iterates D8 neighbors inline | `RegionExplorer.discover(blockPos)` |
| Working set logic | Split between `Region.buildTraceRegions()` and `RiverShaping` | Centralized in `RegionExplorer` |
| Neighbor traversal | D8 (includes diagonals) | D4 (cardinals only) |
| Flow direction filtering | Not implemented | Check border cells flow toward target |
| Early exit | Scattered across `CellCache` and `Region.tracePaths()` | At discovery time in `RegionExplorer` |
| Mixin coupling | Mixin contains logic | Mixin just calls `RiverShaping.shape()` |

## Definition of Done

**What "done" means:**

- `RegionExplorer` class exists with `discover(BlockPos)` method
- `RiverShaping.shape(ChunkAccess)` is the single entry point
- Mixin calls only `RiverShaping.shape()`
- D4 neighbor traversal only
- Border cell flow checks implemented
- Early exit for OCEANIC/INLAND at discovery time
- Unit tests from spec pass
- `/rivertale regionset` command works

**What "done" does NOT mean:**

- Rivers flow (that requires downstream specs)
- Boundaries are identified (next spec)
- Flowlines are traced (later spec)

## Implementation Sequence

```
Phase 1: RiverShaping Entry Point
    ├── Create RiverShaping.shape(ChunkAccess) method
    ├── Move mixin logic into RiverShaping
    └── Mixin becomes thin wrapper

Phase 2: RegionExplorer Class
    ├── Create region/RegionExplorer.java
    ├── Implement discover(BlockPos)
    ├── Implement borderCellsFlowToward()
    └── Add early exit logic

Phase 3: Refactor Existing Code
    ├── Remove Region.buildTraceRegions()
    ├── Remove Region.addRegionChain()
    ├── Update RiverShaping to use RegionExplorer
    └── Clean up scattered early exit checks

Phase 4: Commands
    └── Add /rivertale regionset command
```

---

## Phase 1: RiverShaping Entry Point

### 1.1 Create Single Entry Point

**File:** `world/RiverShaping.java`

The mixin should call one method. All orchestration lives here.

```
static shape(chunk: ChunkAccess):
    center = chunk.getPos().getMiddleBlockPosition(0)
    workingSet = RegionExplorer.discover(center)

    if workingSet.isEmpty():
        return  // OCEANIC or INLAND, nothing to do

    // Future phases will add:
    // - Boundary Identification
    // - Feature Classification
    // - Flowline Tracing
    // - Watershed Building
    // - etc.
```

### 1.2 Mixin (No Changes Needed)

**File:** `mixin/ChunkGeneratorMixin.java`

The mixin is already thin — it just calls `RiverShaping.generateRiverMap(chunk)`. No logic lives in the mixin itself.

When renaming `generateRiverMap` to `shape`, update the mixin call to match. That's the only change.

### 1.3 Refactor RiverShaping.generateRiverMap → shape

Rename `generateRiverMap(ChunkAccess)` to `shape(ChunkAccess)` and replace its body with a call to `RegionExplorer.discover()`.

**Current code (lines 43-50):**
```
regionCache.getOrCompute(center, cellCache, sampleCache)
loadedRegions.add(center)

for dir in FlowDirection.D8:
    neighbor = center.relative(dir)
    regionCache.getOrCompute(neighbor, cellCache, sampleCache)
    loadedRegions.add(neighbor)
```

This D8 iteration is replaced by `RegionExplorer.discover()` which does D4 with proper flow filtering.

---

## Phase 2: RegionExplorer Class

### 2.1 Create Class

**New file:** `region/RegionExplorer.java`

```
class RegionExplorer:

    static discover(blockPos: BlockPos): Set<Region>
        regionPos = RegionPos(blockPos)

        // World boundary check
        if regionPos.containsOutOfBoundsCells():
            return empty set

        currentRegion = RegionCache.get().getOrCompute(regionPos)
        type = currentRegion.type()

        // Early exit for non-river regions
        if type == OCEANIC or type == INLAND:
            return empty set

        // Build working set
        workingSet = new Set<Region>()
        workingSet.add(currentRegion)

        for dir in D4:
            neighborPos = regionPos.relative(dir)

            if neighborPos.containsOutOfBoundsCells():
                continue

            neighbor = RegionCache.get().getOrCompute(neighborPos)

            if type == COASTAL and neighbor.type() == FLUVIAL:
                // COASTAL center: add FLUVIAL neighbors that flow toward us
                if borderCellsFlowToward(neighbor, currentRegion, dir.opposite()):
                    workingSet.add(neighbor)

            else if type == FLUVIAL and neighbor.type() == COASTAL:
                // FLUVIAL center: add COASTAL neighbors we flow toward
                if borderCellsFlowToward(currentRegion, neighbor, dir):
                    workingSet.add(neighbor)

        return workingSet
```

### 2.2 Border Cell Flow Check

```
private static borderCellsFlowToward(source: Region, target: Region, edgeDirection: FlowDirection): boolean
    borderCells = source.pos().getBorderCells(edgeDirection)

    for cellPos in borderCells:
        cell = CellCache.get().getOrComputeWithFlow(cellPos)

        for flowDir in cell.flowDirections():
            neighborPos = cellPos.relative(flowDir)
            if target.pos().contains(neighborPos):
                return true  // Early exit on first match

    return false
```

### 2.3 Key Implementation Notes

**D4 not D8:** The spec requires D4 for region neighbor traversal. Rivers cross region boundaries cardinally only. The current code at `Region.java:103` uses D8 — this is the primary bug.

**Flow direction check:** The current code at `Region.java:102-110` only checks neighbor type, not whether border cells actually flow toward the target. This filter is cheap (data already cached) and avoids processing irrelevant neighbors.

**Early exit order:** Check OCEANIC first, then INLAND. OCEANIC is a true early exit (only examined current region's cells). INLAND already sampled neighbor cells during classification, so the "early exit" just saves flow direction checks.

**getOrComputeWithFlow:** Use this method (from Spatial Infrastructure) to ensure flow directions are computed before checking them. Plain `getOrCompute()` returns cells without flow.

---

## Phase 3: Refactor Existing Code

### 3.1 Remove Region.buildTraceRegions()

**File:** `region/Region.java`

Delete lines 82-89. This method is replaced by `RegionExplorer.discover()`.

### 3.2 Remove Region.addRegionChain()

**File:** `region/Region.java`

Delete lines 91-111. This recursive method is replaced by the flat loop in `RegionExplorer.discover()` with corrections (D4, flow checks).

### 3.3 Update Callers

Find all places that called `buildTraceRegions()` or `addRegionChain()` and update them to use `RegionExplorer.discover()`.

### 3.4 Clean Up Scattered Early Exits

Sage noted early exits in:
- `CellCache` line 79
- `Region.computePaths()` line 56

Review these. Early exit now happens in `RegionExplorer.discover()` — if we get past that, we know we're processing a valid region. The scattered checks may be redundant or may serve a different purpose. Remove if redundant; leave if they guard against different conditions.

---

## Phase 4: Commands

### 4.1 Add /rivertale regionset Command

**New file:** `command/RegionSetCommand.java`

```
/rivertale regionset <x> <z>
```

Output format:
```
Region Set for (x, z):
  Center: RegionPos(0, 0) - COASTAL
  Working set size: 3

  Included regions:
    - RegionPos(0, 0) COASTAL (center)
    - RegionPos(0, -1) FLUVIAL (north, flows south)
    - RegionPos(1, 0) FLUVIAL (east, flows west)

  Excluded neighbors:
    - RegionPos(-1, 0) OCEANIC (west)
    - RegionPos(0, 1) INLAND (south)
```

This helps debug why regions are or aren't included in the working set.

---

## Validation Checklist

### Unit Tests Required

From the Level 4 spec:

| Test | Setup | Expected |
|------|-------|----------|
| OCEANIC center | All cells ocean | Empty set |
| INLAND center | No ocean, no D4 COASTAL | Empty set |
| COASTAL, no FLUVIAL | All neighbors OCEANIC/COASTAL | Set of 1 |
| COASTAL, FLUVIAL flows in | FLUVIAL north with south-flowing border | Set of 2 |
| COASTAL, FLUVIAL flows away | FLUVIAL north with north-flowing border | Set of 1 |
| FLUVIAL, drains to COASTAL | Border cells flow toward COASTAL south | Set of 2 |
| FLUVIAL, no drainage | Border cells flow toward INLAND/FLUVIAL | Set of 1 |
| FLUVIAL, multiple drainage | Flows toward COASTAL east and west | Set of 3 |
| World border | West neighbor out of bounds | West not in set |
| Corner diagonal flow | Corner flows NORTHEAST, target is NORTH | Cell doesn't qualify |

### Integration Points

After this implementation:
- `RiverShaping.shape()` receives a filtered working set
- Boundary Identification can iterate the working set
- OCEANIC/INLAND chunks early-exit before any downstream work

---

## Migration Notes

### Breaking Changes

- `Region.buildTraceRegions()` removed
- `Region.addRegionChain()` removed
- Callers must use `RegionExplorer.discover()` instead

### Dependencies

Requires from Spatial Infrastructure:
- `RegionPos.getBorderCells(FlowDirection)`
- `RegionPos.containsOutOfBoundsCells()`
- `RegionPos.contains(CellPos)` — already exists
- `CellCache.getOrComputeWithFlow(CellPos)`
- `FlowDirection.D4` constant

If these aren't implemented yet, this spec is blocked on Spatial Infrastructure completion.
