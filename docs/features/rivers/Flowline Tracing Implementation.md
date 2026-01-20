---
level: 5
parent: "[[Flowline Tracing]]"
status: draft
---

# Flowline Tracing — Implementation

This document provides implementation guidance for the [[Flowline Tracing]] specification. The spec is the source of truth; this document describes how to refactor the existing exploratory code to match.

## Scope

This implementation covers:
- Renaming `Path` class to `Flowline` record
- Creating `FlowlineTracer` class
- Fixing terminus cell collection (critical bug)
- Replacing near-terminus alignment scoring with brute-force
- Implementing nudge fallback with flow direction rotation
- Removing mid-trace basin detection

This does NOT cover:
- Merging flowlines (see [[Watershed Building Implementation]])
- Validating path lengths (see [[Watershed Validation Implementation]])

## Overview

The tracing algorithm core is correct, but terminus collection has a critical bug and the near/far mode behavior diverges from spec:

| Area | Current State | Target State |
|------|---------------|--------------|
| Class name | `Path` | `Flowline` record + `FlowlineTracer` |
| Terminus collection | `boundary.ocean()` (ocean cells) | `boundary.cells()` (land cells) |
| Near-terminus mode | Alignment scoring among flow directions | Brute-force D8 toward terminus |
| Fallback | Brute-force D8 | Nudge: rotate flow directions toward terminus |
| Basin terminus | `isBasinTerminus()` mid-trace | Pre-identified in boundaries |
| Variable naming | `oceanCells`, `findNearestOceanCell()` | `terminusCells`, `findNearestTerminus()` |

## Definition of Done

**What "done" means:**

- `Flowline` record exists with `cells`, `terminus`, `isValid` fields
- `FlowlineTracer` class coordinates tracing for a region
- Terminus cells are land cells from ocean boundaries + basin cells from basin boundaries
- Near-terminus uses brute-force D8
- Far-terminus uses steepest downhill with progress check
- Fallback uses rotated flow directions
- `isBasinTerminus()` method removed
- Unit tests from spec pass

**What "done" does NOT mean:**

- Flowlines merge into watersheds (that's Watershed Building)
- Path length validation (that's Watershed Validation)

## Implementation Sequence

```
Phase 1: Data Structure
    ├── Create Flowline record
    └── Create FlowlineTracer class shell

Phase 2: Terminus Collection Fix
    ├── Rename oceanCells → terminusCells
    ├── Fix collection to use boundary.cells()
    └── Rename findNearestOceanCell → findNearestTerminus

Phase 3: Near-Terminus Mode Fix
    └── Replace followAlignedFlow with forceTowardTerminus

Phase 4: Nudge Fallback Implementation
    ├── Implement nudgeTowardTerminus
    └── Update fallback to use nudge before brute-force

Phase 5: Basin Terminus Cleanup
    └── Remove isBasinTerminus method

Phase 6: Migration
    ├── Update callers to use FlowlineTracer
    └── Delete Path class
```

---

## Phase 1: Data Structure

### 1.1 Create Flowline Record

**New file:** `river/Flowline.java`

```
record Flowline(
    List<CellPos> cells,    // ordered from source to terminus
    CellPos terminus,       // final cell (ocean boundary or basin boundary)
    boolean isValid         // false if trace failed
) {}
```

The record is immutable. Tracing state stays in the tracer; the record is only created when tracing completes.

### 1.2 Create FlowlineTracer Class

**New file:** `river/FlowlineTracer.java`

```
class FlowlineTracer:
    terminusCells: Set<CellPos>
    allowedRegions: Set<RegionPos>

    FlowlineTracer(regions: Set<Region>):
        terminusCells = collectTerminusCells(regions)
        allowedRegions = regions.stream()
            .map(Region::pos)
            .collect(toSet())

    trace(source: CellPos): Flowline
        // Main trace loop (moved from Path)

    private collectTerminusCells(regions): Set<CellPos>
        // Collects from all boundaries
```

The tracer is created once per working set, then `trace()` is called for each SOURCE cell.

---

## Phase 2: Terminus Collection Fix

### 2.1 Critical Bug — Wrong Terminus Cells

**Current (Path.java lines 36-38):**
```
for (OceanBoundary boundary : region.boundaries()) {
    this.oceanCells.add(boundary.ocean());  // WRONG: adds ocean cells
}
```

**Problem:** Rivers trace *to* ocean cells, ending up in the water. The spec says rivers should stop at the coastline—land cells adjacent to ocean.

**Target:**
```
collectTerminusCells(regions: Set<Region>): Set<CellPos>
    terminusCells = new Set<CellPos>()

    for region in regions:
        for boundary in region.boundaries():
            for cell in boundary.cells():  // land cells for OCEAN, basin cells for BASIN
                terminusCells.add(cell)

    return terminusCells
```

After Boundary Identification refactor, `boundary.cells()` returns:
- For OCEAN boundaries: land cells adjacent to ocean
- For BASIN boundaries: below-sea-level cells

Both are valid terminus candidates.

### 2.2 Rename Variables and Methods

| Old Name | New Name | Location |
|----------|----------|----------|
| `oceanCells` | `terminusCells` | Field |
| `findNearestOceanCell()` | `findNearestTerminus()` | Method |
| `forceTowardOcean()` | `forceTowardTerminus()` | Method |

---

## Phase 3: Near-Terminus Mode Fix

### 3.1 Replace Alignment Scoring

**Current (lines 148-175):**
```
followAlignedFlow(current, flowDirections, nearestOcean, currentDistance):
    // Scores flow directions by alignment with terminus direction
    // Returns best-aligned flow direction that makes progress
```

**Problem:** The spec says near-terminus mode should ignore flow directions entirely and brute-force toward the terminus. We've done the terrain-respecting work to get close; now just close the gap.

**Target:**
```
forceTowardTerminus(current, terminus, currentDistance, allowedRegions):
    best = null
    bestDist = currentDistance

    for dir in FlowDirection.D8:
        next = current.relative(dir)

        if not allowedRegions.contains(next.getRegion()):
            continue

        dist = distance(next, terminus)
        if dist < bestDist:
            bestDist = dist
            best = next

    return best
```

This is actually what the current `forceTowardOcean()` does. The fix is to use this function in near-terminus mode instead of `followAlignedFlow()`.

### 3.2 Update followFlow

**Current structure:**
```
if currentDistance <= mergeThreshold:
    aligned = followAlignedFlow(...)  // alignment scoring
    if aligned != null:
        return aligned
else:
    // far mode: steepest downhill with progress
```

**Target structure:**
```
selectNextCell(current, nearestTerminus, allowedRegions):
    cell = CellCache.get().getOrCompute(current)
    currentDist = distance(current, nearestTerminus)

    // Mode 1: Near terminus — brute-force
    if currentDist <= mergeThreshold:
        return forceTowardTerminus(current, nearestTerminus, currentDist, allowedRegions)

    // Mode 2: Far from terminus — steepest downhill with progress
    for dir in cell.flowDirections():
        next = current.relative(dir)

        if not allowedRegions.contains(next.getRegion()):
            continue

        if distance(next, nearestTerminus) < currentDist:
            return next

    // Fallback: Nudge
    return nudgeTowardTerminus(current, cell, nearestTerminus, currentDist, allowedRegions)
```

---

## Phase 4: Nudge Fallback Implementation

### 4.1 Implement Nudge

**Current fallback (lines 143-145):**
```
// Falls through to forceTowardOcean (brute-force D8)
```

**Problem:** The spec says far-from-terminus fallback should still respect terrain by rotating flow directions toward the terminus, not brute-force.

**Target:**
```
nudgeTowardTerminus(current, cell, terminus, currentDist, allowedRegions):
    towardTerminus = FlowDirection.toward(current, terminus)

    for dir in cell.flowDirections():
        rotated = dir.rotateToward(towardTerminus)
        next = current.relative(rotated)

        if not allowedRegions.contains(next.getRegion()):
            continue

        if distance(next, terminus) < currentDist:
            return next

    return null  // nudge failed, flowline invalid
```

### 4.2 Dependencies

Requires from Spatial Infrastructure:
- `FlowDirection.toward(CellPos from, CellPos to)` — returns the D8 direction from `from` toward `to`
- `FlowDirection.rotateToward(FlowDirection target)` — rotates this direction one step toward target

If nudge returns null, the flowline is marked invalid. Unlike current implementation, there is no brute-force fallback for far-from-terminus mode.

### 4.3 Invalid Flowlines

When nudge fails, the flowline is invalid. This means terrain doesn't drain toward any terminus from this source—even rotating flow directions doesn't help.

Invalid flowlines are discarded, not passed to Watershed Building.

---

## Phase 5: Basin Terminus Cleanup

### 5.1 Remove isBasinTerminus

**Current (lines 202-222):**
```
isBasinTerminus(pos):
    // Checks if pos is below sea level AND a local minimum (no lower D8 neighbors)
```

**Why remove:** Basin terminus cells are now pre-identified by Boundary Identification and included in `terminusCells`. The main terminus check handles both:

```
if terminusCells.contains(current):
    return Flowline(state.cells, current, true)
```

Delete `isBasinTerminus()` entirely. The mid-trace check at lines 53-56 also goes away.

### 5.2 Semantic Change Note

The old `isBasinTerminus()` required two conditions:
1. Cell below sea level
2. Local minimum (no lower D8 neighbors)

The new approach only requires condition 1 (via `Cell.isBasin()` in Boundary Identification). This is intentional—all below-sea-level cells are valid terminus candidates, not just local minima. This simplifies the algorithm.

---

## Phase 6: Migration

### 6.1 Update Callers

**Migration note:** The current flowline tracing logic lives in `Region.tracePaths()` (lines 61-79). This method is deleted entirely—Region no longer handles path computation. FlowlineTracer takes over this responsibility and is called from RiverShaping.

**File:** `world/RiverShaping.java`

Replace the tracing responsibility (currently in Region) with FlowlineTracer:

```
// Before
for source in sourceCells:
    path = new Path(source, regions)
    path.trace()
    if path.isValid():
        // use path

// After
tracer = new FlowlineTracer(regions)
flowlines = new List<Flowline>()

for source in sourceCells:
    flowline = tracer.trace(source)
    if flowline.isValid():
        flowlines.add(flowline)

grouped = groupByTerminus(flowlines)
// pass to Watershed Building
```

### 6.2 Delete Path Class

Once all callers are migrated, delete `river/Path.java`.

### 6.3 Grouping by Terminus

Add grouping logic to FlowlineTracer or RiverShaping:

```
groupByTerminus(flowlines: List<Flowline>): Map<CellPos, List<Flowline>>
    groups = new Map<CellPos, List<Flowline>>()

    for flowline in flowlines:
        if not flowline.isValid():
            continue

        terminus = flowline.terminus()
        groups.computeIfAbsent(terminus, k -> new List<>()).add(flowline)

    return groups
```

---

## Wiring into RiverShaping

This section describes how Flowline Tracing integrates into the `RiverShaping.shape()` pipeline. Flowline Tracing runs after early Feature Classification and produces grouped flowlines for Watershed Building.

### Current State (after Feature Classification early phase)

```
public static void shape(ChunkAccess chunk, int seaLevel):
    // Region Discovery
    workingSet = RegionExplorer.discover(...)
    if workingSet.isEmpty():
        return

    // Boundary Identification
    for region in workingSet:
        boundaries = switch region.type():
            case COASTAL -> Coastal.boundaries(region.pos(), cellCache)
            case FLUVIAL -> Fluvial.boundaries(region.pos(), cellCache)
            default -> List.of()

        enriched = region.withBoundaries(boundaries)
        RegionCache.get().put(enriched)

    // Feature Classification (early phase)
    for region in workingSet:
        for cellPos in region.cells():
            cell = CellCache.get().getOrCompute(cellPos)
            classified = Feature.classify(cell, null)
            CellCache.get().put(classified)
```

### This Implementation Adds

**Flowline Tracing** — After early classification. Creates FlowlineTracer once per working set, traces from each SOURCE cell, groups results by terminus.

```
public static void shape(ChunkAccess chunk, int seaLevel):
    // ... Region Discovery, Boundary Identification, Feature Classification unchanged ...

    // Flowline Tracing — NEW
    tracer = new FlowlineTracer(workingSet)
    allFlowlines = new List<Flowline>()

    for region in workingSet:
        for cellPos in region.cells():
            cell = CellCache.get().getOrCompute(cellPos)
            if cell.feature() == Feature.SOURCE:
                flowline = tracer.trace(cellPos)
                if flowline.isValid():
                    allFlowlines.add(flowline)

    // Group by terminus for Watershed Building — NEW
    flowlineGroups = groupByTerminus(allFlowlines)

    // ... Watershed Building, Validation, etc. follow ...
    // ... Reclassification still at the end ...
```

**Helper method:**

```
groupByTerminus(flowlines: List<Flowline>): Map<CellPos, List<Flowline>>
    groups = new Map<CellPos, List<Flowline>>()

    for flowline in flowlines:
        terminus = flowline.terminus()
        groups.computeIfAbsent(terminus, k -> new List<>()).add(flowline)

    return groups
```

### Validation at This Stage

After this implementation, you can verify:
- SOURCE cells produce flowlines
- Flowlines trace from source to terminus without cycles
- Invalid flowlines (stuck, cycle, DIVIDE blocked) are filtered out
- Multiple flowlines to the same terminus are grouped together
- FlowlineTracer collects terminus cells correctly from boundaries

You **cannot** yet verify:
- Watershed graph structure (that's Watershed Building)
- Whether flowlines merge correctly at confluences (that's Watershed Building)

---

## Validation Checklist

### Unit Tests Required

From the Level 4 spec:

| Test | Setup | Expected |
|------|-------|----------|
| Simple downhill | SOURCE with clear flow to terminus | Valid flowline |
| Cycle detection | Flow directions create loop | Invalid flowline |
| DIVIDE blocking | Next cell is DIVIDE | Invalid flowline |
| No terminus cells | Empty terminus set | Invalid flowline |
| Brute-force mode | Source within mergeThreshold | Uses D8, ignores flow |
| Nudge success | Flow directions don't make progress (far) | Rotated direction used |
| Nudge fails | Even rotated directions don't make progress | Invalid flowline |
| Cross-region valid | FLUVIAL flows into COASTAL | Valid |
| Cross-region blocked | Flow outside working set | Direction skipped |
| Same terminus | Two sources reach same terminus | Both grouped together |
| Source at terminus | SOURCE in terminus set | Single-cell flowline |
| Diagonal flow | Steepest is diagonal | Diagonal neighbor selected |

### Integration Points

After this implementation:
- Watershed Building can receive grouped flowlines
- Each group shares a terminus cell
- Invalid flowlines are already filtered out

---

## Command Updates

### Update /rivertale flowline Command

Existing commands that reference `Path` should be updated to use `Flowline`:
- `/rivertale flowline <x> <z>` — trace from cell, show path and validity

### Add Visualization

`/rivertale vis flowlines` — spawn particles along flowline paths for debugging.

---

## Migration Notes

### Breaking Changes

- `Path` class deleted, use `Flowline` record
- Constructor changes (FlowlineTracer takes regions, trace() takes source)
- `encode()`/`decode()` move to Flowline record

### Dependencies

Requires from Spatial Infrastructure:
- `FlowDirection.toward(CellPos, CellPos)` — direction calculation
- `FlowDirection.rotateToward(FlowDirection)` — rotation logic
- `FlowDirection.D8` — all eight directions

Requires from Boundary Identification:
- `Region.boundaries()` returns `List<Boundary>`
- `Boundary.cells()` returns terminus candidate cells

### Timer Updates

Update timer names:
- `Store.getTimer(Path.class, "trace")` → `Store.getTimer(FlowlineTracer.class, "trace")`
- `Store.getTimer(Path.class, "findNearestOceanCell")` → `Store.getTimer(FlowlineTracer.class, "findNearestTerminus")`
- `Store.getTimer(Path.class, "followFlow")` → `Store.getTimer(FlowlineTracer.class, "selectNextCell")`
