---
level: 5
parent: "[[Watershed Validation]]"
status: draft
---

# Watershed Validation — Implementation

This document provides implementation guidance for the [[Watershed Validation]] specification. The spec is the source of truth; this document describes how to refactor the existing exploratory code to match.

## Scope

This implementation covers:
- Extracting `pruneShortTributaries()` from Watershed constructor to public `validate()` method
- Moving cell membership marking from WatershedCache to validation
- Aligning `minPathLength` config with spec defaults

This does NOT cover:
- Pruning algorithm changes (already matches spec)
- Elevation assignment (see [[Elevation Assignment Implementation]])

## Overview

The pruning algorithm matches the spec. The divergences are structural—where the code lives and when it runs:

| Area | Current State | Target State |
|------|---------------|--------------|
| Pruning location | `pruneShortTributaries()` in Watershed constructor | Public `validate()` method called by RiverShaping |
| Cell marking | `WatershedCache.finalizeWatershed()` lines 80-83 | Inside `validate()` after pruning |
| minPathLength default | 3 | 5 |
| minPathLength range | 1-20 | 1-32 |

## Definition of Done

**What "done" means:**

- `Watershed.validate()` is public and called by RiverShaping
- Watershed constructor does not call pruning
- Cell membership marking happens inside `validate()` using `cell.withWatershed()`
- Config aligned with spec (default 5, range 1-32)
- Unit tests from spec pass

**What "done" does NOT mean:**

- Algorithm changes (pruning logic is already correct)
- Elevation assignment (separate pipeline stage)

## Implementation Sequence

```
Phase 1: Extract validate() Method
    └── Rename pruneShortTributaries() to validate() and make public

Phase 2: Add Cell Membership Marking
    └── Mark surviving cells after pruning loop

Phase 3: Update Config
    ├── Change minPathLength default to 5
    └── Change minPathLength range to 1-32

Phase 4: Update Callers
    ├── RiverShaping calls watershed.validate() after building
    └── Remove cell marking from WatershedCache
```

---

## Phase 1: Extract validate() Method

### 1.1 Rename and Make Public

**File:** `river/Watershed.java`

**Current (lines 129-147):**
```
private void pruneShortTributaries() {
    int minLength = CommonConfig.get().minPathLength();
    boolean pruned;

    do {
        pruned = false;
        Set<CellPos> sources = findSources();

        for (CellPos source : sources) {
            List<CellPos> segment = traceToConfluenceOrTerminus(source);
            if (segment.size() < minLength) {
                pruneSegment(segment);
                pruned = true;
            }
        }
    } while (pruned);

    upstreamCountCache.clear();
}
```

**Target:**
```
public void validate():
    minLength = CommonConfig.get().minPathLength()
    pruned = true

    while pruned:
        pruned = false
        sources = findSources()

        for source in sources:
            segment = traceToConfluenceOrTerminus(source)
            if segment.size() < minLength:
                pruneSegment(segment)
                pruned = true

    upstreamCountCache.clear()

    // Mark surviving cells (added in Phase 2)
```

**Note:** The constructor call to `pruneShortTributaries()` is removed by [[Watershed Building Implementation]]. By the time this implementation runs, the method is private and not called from the constructor.

---

## Phase 2: Add Cell Membership Marking

### 2.1 Mark Cells After Pruning

**Add to end of `validate()` method:**

```
validate():
    // ... pruning loop from Phase 1 ...

    // Mark surviving cells as members of this watershed
    cellCache = CellCache.get()
    for cellPos in allCells():
        cell = cellCache.getOrCompute(cellPos)
        cellCache.put(cell.withWatershed(terminus))
```

This ensures only cells that survive pruning get marked. Pruned cells retain no watershed reference.

### 2.2 Dependency

Requires from Spatial Infrastructure:
- `Cell.withWatershed(CellPos terminus)` mutation method
- `Cell.terminus()` accessor

---

## Phase 3: Update Config

### 3.1 Change Default

**File:** `config/CommonConfig.java`

**Current (line 20, approximate):**
```
minPathLength = 3
```

**Target:**
```
minPathLength = 5
```

### 3.2 Change Range

**Current (line 87, approximate):**
```
range: 1-20
```

**Target:**
```
range: 1-32
```

A value of 32 allows aggressive pruning that removes all but the longest tributaries, useful for worlds with dense river networks.

---

## Phase 4: Update Callers

### 4.1 RiverShaping Calls validate()

**File:** `world/RiverShaping.java`

After building watersheds, call validation:

```
// Watershed Building
for (terminus, flowlines) in flowlineGroups:
    watershed = new Watershed(terminus, flowlines)
    WatershedCache.get().put(watershed)

// Watershed Validation
for watershed in WatershedCache.get().all():
    watershed.validate()
```

### 4.2 Remove Cell Marking from WatershedCache

**File:** `river/WatershedCache.java`

**Delete from `finalizeWatershed()` (lines 80-83):**
```
for (CellPos cell : watershed.allCells()) {
    cellToTerminus.put(cell, terminus);
}
```

This loop is replaced by the marking inside `validate()`. The `cellToTerminus` map is being removed entirely per Watershed Building Implementation.

### 4.3 Add all() Method to WatershedCache

To iterate over all watersheds for validation:

```
all(): Collection<Watershed>
    return cache.values()
```

---

## Unchanged Code

### Pruning Algorithm

**File:** `river/Watershed.java` lines 129-146

The iteration-until-stable pattern matches the spec exactly:
- Finds sources (cells with no upstream)
- Traces each source to confluence or terminus
- Prunes segments shorter than minPathLength
- Repeats until no pruning occurs

No algorithm changes needed.

### findSources()

**File:** `river/Watershed.java` lines 149-158

Matches spec. No changes needed.

### traceToConfluenceOrTerminus()

**File:** `river/Watershed.java` lines 160-181

Matches spec. Excludes confluence cell from segment, which is correct. No changes needed.

### pruneSegment()

**File:** `river/Watershed.java` lines 183-202

Matches spec. Disconnects from downstream, removes all cells in segment. No changes needed.

---

## Wiring into RiverShaping

This section describes how Watershed Validation integrates into the `RiverShaping.shape()` pipeline. Validation prunes short tributaries and marks cell membership.

### Current State (after Watershed Building)

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
```

### This Implementation Adds

**Watershed Validation** — After building. Prunes short tributaries, marks surviving cells, stores non-empty watersheds.

```
public static void shape(ChunkAccess chunk, int seaLevel):
    // ... Region Discovery, Boundary Identification, Feature Classification, Flowline Tracing, Watershed Building unchanged ...

    // Watershed Validation — NEW
    validWatersheds = new List<Watershed>()
    for watershed in watersheds:
        watershed.validate()

        // Skip empty watersheds (all tributaries pruned)
        if watershed.allCells().isEmpty():
            continue

        WatershedCache.get().put(watershed)
        validWatersheds.add(watershed)

    // ... Elevation Assignment, Flow Accumulation, Reclassification follow ...
```

**Note:** Only non-empty watersheds are stored in `WatershedCache`. Watersheds that get completely pruned (all segments shorter than `minPathLength`) are discarded.

### Validation at This Stage

After this implementation, you can verify:
- Short tributaries are pruned
- Pruning cascades until stable
- Surviving cells have terminus set via `cell.withWatershed()`
- Empty watersheds are not stored in cache
- Confluence cells survive pruning (they mark where tributaries join)

You **cannot** yet verify:
- Cells have elevation data (that's Elevation Assignment)
- Cells have width/depth data (that's Flow Accumulation)
- Final feature classification (that's Reclassification)

---

## Validation Checklist

### Unit Tests Required

From the Level 4 spec:

| Test | Setup | Expected |
|------|-------|----------|
| No pruning needed | All segments >= minPathLength | Watershed unchanged |
| Single short tributary | Main path + 2-cell tributary, min=3 | Tributary pruned |
| Cascade pruning | Nested short tributaries | All short segments removed |
| Entire watershed too short | 3-cell path, min=5 | Empty watershed |
| Multiple short tributaries | Three 2-cell tributaries, min=3 | All three pruned |
| Surviving cells marked | 5-cell path after validation | All 5 have terminus set |
| Pruned cells not marked | Short tributary pruned | Pruned cells have no terminus |

### Integration Points

After this implementation:
- Elevation Assignment receives validated watersheds
- Only surviving cells have watershed membership
- Empty watersheds can be detected and skipped

---

## Migration Notes

### Breaking Changes

- `pruneShortTributaries()` renamed to `validate()` and made public
- Watershed constructor no longer prunes
- Cell marking moves from WatershedCache to Watershed.validate()
- minPathLength default changes from 3 to 5

### Dependencies

Requires from Spatial Infrastructure:
- `Cell.withWatershed(CellPos terminus)` mutation method
- `Cell.terminus()` accessor

Requires from Watershed Building:
- Watershed.terminus field (for marking cells)

### Timer Updates

Add timer for validation:
```
Store.getTimer(Watershed.class, "validate")
```

The existing timers for findSources, traceToConfluenceOrTerminus, and pruneSegment can remain as sub-timers if desired.
