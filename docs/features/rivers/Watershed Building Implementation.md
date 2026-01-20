---
level: 5
parent: "[[Watershed Building]]"
status: draft
---

# Watershed Building — Implementation

This document provides implementation guidance for the [[Watershed Building]] specification. The spec is the source of truth; this document describes how to refactor the existing exploratory code to match.

## Scope

This implementation covers:
- Changing Watershed key from `RegionPos` to terminus `CellPos`
- Simplifying WatershedCache (removing pending/finalized lifecycle)
- Extracting Y assignment and reclassification from constructor
- Adding explicit `terminus` field to Watershed
- Removing external cell-to-terminus tracking

This does NOT cover:
- Path length validation (see [[Watershed Validation Implementation]])
- Elevation assignment algorithm (see [[Elevation Assignment Implementation]])
- Cell membership marking (happens during Watershed Validation)

## Overview

The core graph-building and diagonal crossing resolution match the spec. The divergences are in structure and lifecycle:

| Area | Current State | Target State |
|------|---------------|--------------|
| Watershed key | `RegionPos pos` | `CellPos terminus` |
| Constructor input | `RegionPos, List<Path>` | `CellPos terminus, List<Flowline>` |
| Constructor behavior | Builds graph + prunes + assigns Y + reclassifies | Builds graph only |
| WatershedCache | Pending/finalized split, PendingWatershed class | Simple cache keyed by terminus |
| Cell membership | External `cellToTerminus` map | Stored on Cell via `withWatershed()` |
| terminus() method | Derives by tracing downstream | Returns explicit field |

## Definition of Done

**What "done" means:**

- Watershed record has `terminus: CellPos` field
- Constructor takes `(terminus, flowlines)` and builds graph only
- WatershedCache is simple: `put()`, `getOrCompute()`, `getIfPresent()`, `clear()`
- PendingWatershed class deleted
- External cellToTerminus map removed
- Diagonal crossing detection/resolution unchanged (already correct)
- Unit tests from spec pass

**What "done" does NOT mean:**

- Cell membership marking (that's Watershed Validation)
- Y level assignment (that's Elevation Assignment)
- Tributary pruning (that's Watershed Validation)

## Implementation Sequence

```
Phase 1: Watershed Record Restructure
    ├── Change pos field to terminus
    ├── Update constructor signature
    └── Add explicit terminus() accessor

Phase 2: Extract Pipeline Stages
    ├── Remove pruneShortTributaries() call from constructor
    ├── Remove assignYLevels() call from constructor
    └── Remove reclassify() call from constructor

Phase 3: Simplify WatershedCache
    ├── Remove pending/finalized split
    ├── Delete PendingWatershed class
    ├── Change key from RegionPos to CellPos
    └── Remove external cellToTerminus map

Phase 4: Update Callers
    ├── RiverShaping calls pipeline stages separately
    └── Cell membership marking moves to Watershed Validation
```

---

## Phase 1: Watershed Record Restructure

### 1.1 Change Key Field

**File:** `river/Watershed.java`

**Current (line 23):**
```
private final RegionPos pos;
```

**Target:**
```
private final CellPos terminus;
```

A region may contain multiple watersheds (different termini). The spec identifies watersheds by terminus, not by region.

### 1.2 Update Constructor Signature

**Current (line 28):**
```
Watershed(RegionPos pos, List<Path> paths)
```

**Target:**
```
Watershed(CellPos terminus, List<Flowline> flowlines)
```

The constructor receives flowlines already grouped by terminus from Flowline Tracing.

### 1.3 Add Explicit terminus() Accessor

**Current (lines 332-342):**
```
terminus(cell: CellPos): Optional<CellPos>
    // Traces downstream to find terminus
```

**Target:**
```
terminus(): CellPos
    return terminus
```

The terminus is stored explicitly, not derived. The current method that takes a cell parameter is unnecessary—callers should already know they're querying the right watershed.

### 1.4 Update pos() to terminus()

**Current:**
```
pos(): RegionPos
    return pos
```

**Target:**
```
terminus(): CellPos
    return terminus
```

Rename accessor to match field.

---

## Phase 2: Extract Pipeline Stages

### 2.1 Remove from Constructor

**Current constructor (lines 28-42):**
```
Watershed(pos, paths):
    this.pos = pos

    for path in paths:
        addPath(path)

    pruneShortTributaries()  // Remove
    assignYLevels()          // Remove
    reclassify()             // Remove
```

**Target constructor:**
```
Watershed(terminus, flowlines):
    this.terminus = terminus

    for flowline in flowlines:
        addFlowline(flowline)

    // Mark terminus cell (downstream = null)
    if not flowlines.isEmpty():
        downstream.put(terminus, null)
```

The constructor builds the graph only. Pruning, elevation assignment, and reclassification are separate pipeline stages called by RiverShaping.

### 2.2 Pipeline Stage Methods

The methods removed from the constructor belong to downstream pipeline stages:

- `pruneShortTributaries()` — stays private; [[Watershed Validation Implementation]] will extract, rename to `validate()`, and make public
- `assignYLevels()` — [[Elevation Assignment Implementation]] will handle
- `reclassify()` — called after Feature Classification

This doc removes the constructor calls. The downstream docs handle making the methods callable from RiverShaping.

### 2.3 Rename addPath to addFlowline

**Current:**
```
private addPath(Path path)
```

**Target:**
```
private addFlowline(Flowline flowline)
```

Internal consistency with new naming.

---

## Phase 3: Simplify WatershedCache

### 3.1 Remove Pending/Finalized Split

**Current structure (lines 15-18):**
```
pending: Map<CellPos, PendingWatershed>
finalized: Map<CellPos, Watershed>
cellToTerminus: Map<CellPos, CellPos>
regionToTermini: Map<RegionPos, Set<CellPos>>
```

**Target structure:**
```
cache: Map<Long, Watershed>  // keyed by terminus.toLong()
```

The spec's model is simpler: build watershed, store via `put()`, query via `getOrCompute()`. No staging.

### 3.2 Delete PendingWatershed

**Delete file:** `river/PendingWatershed.java`

The pending watershed concept exists because the current implementation groups paths after they're traced. The spec groups flowlines by terminus during Flowline Tracing, then builds watersheds directly.

### 3.3 Update Cache Interface

**Target interface:**
```
class WatershedCache:
    private cache: Map<Long, Watershed>

    static get(): WatershedCache
        // singleton accessor

    put(watershed: Watershed): void
        cache.put(watershed.terminus().toLong(), watershed)

    getOrCompute(x: int, z: int): Watershed
        key = CellPos.toLong(x, z)
        watershed = cache.get(key)
        if watershed == null:
            throw IllegalStateException("No watershed at terminus ($x, $z)")
        return watershed

    getIfPresent(x: int, z: int): Watershed
        return cache.get(CellPos.toLong(x, z))

    getIfPresent(terminus: CellPos): Watershed
        return cache.get(terminus.toLong())

    clear(): void
        cache.clear()
```

`getOrCompute()` throws if not found because watersheds are not computed on demand—they're built during the pipeline and stored. A miss indicates a bug.

### 3.4 Remove External Cell-to-Terminus Map

**Current (lines 81-83):**
```
for cell in watershed.allCells():
    cellToTerminus.put(cell, terminus)
```

**Target:** Delete this loop.

Cell membership is tracked on the Cell record itself via `Cell.withWatershed(terminus)`. This happens during Watershed Validation, not during cache storage.

### 3.5 Remove regionToTermini Map

**Current:** Tracks which termini belong to which region for eviction.

**Target:** Delete. The cache is cleared entirely when needed, not evicted per-region. Region-based eviction was for the pending/finalized lifecycle that no longer exists.

### 3.6 Remove addPath and finalizeReady Methods

**Delete:**
- `addPath(Path path)` — flowlines are grouped before watershed building
- `finalizeReady(Set<RegionPos>)` — no pending/finalized distinction

### 3.7 Update getWatershed to getIfPresent

**Current (lines 86-91):**
```
getWatershed(cell: CellPos): Watershed
    terminus = cellToTerminus.get(cell)
    if terminus == null:
        return null
    return finalized.get(terminus)
```

**Target:**
```
// To find watershed for a cell, first get the cell's terminus
cell = CellCache.get().getOrCompute(pos)
if cell.terminus() != null:
    watershed = WatershedCache.get().getIfPresent(cell.terminus())
```

The lookup pattern changes. Callers query the cell for its terminus, then query the cache for that terminus's watershed.

---

## Phase 4: Update Callers

### 4.1 RiverShaping Pipeline

**Current flow:**
1. Trace paths
2. Add paths to WatershedCache (pending)
3. Finalize watersheds (builds graph + prunes + assigns Y + reclassifies)

**Target flow:**
```
// Flowline Tracing
flowlineGroups = groupByTerminus(traceAllSources(regions))

// Watershed Building
for (terminus, flowlines) in flowlineGroups:
    watershed = new Watershed(terminus, flowlines)
    WatershedCache.get().put(watershed)

// Watershed Validation (separate pipeline stage)
for watershed in WatershedCache.get().all():
    watershed.prune()
    // Mark cells with terminus

// Elevation Assignment (separate pipeline stage)
for watershed in WatershedCache.get().all():
    assignElevations(watershed)

// Reclassification (separate pipeline stage)
for watershed in WatershedCache.get().all():
    reclassify(watershed)
```

### 4.2 Cell Membership Marking

**Current:** Done in `WatershedCache.finalizeWatershed()` via external map.

**Target:** Done in Watershed Validation after pruning:

```
// In Watershed Validation
for cell in watershed.allCells():
    enrichedCell = CellCache.get().getOrCompute(cell)
        .withWatershed(watershed.terminus())
    CellCache.put(enrichedCell)
```

This ensures only cells that survive pruning are marked with watershed membership.

---

## Unchanged Code

### Diagonal Crossing Detection

**File:** `river/Watershed.java` lines 83-107

The `detectDiagonalCrossing()` method matches the spec exactly. No changes needed.

### Diagonal Crossing Resolution

The resolution logic in `addPath()` (lines 53-68) matches the spec's `resolveCrossing()` algorithm:
- Compare accumulation
- Lower accumulation yields
- Winner continues, loser merges at crossing point

No changes needed.

### Prune Downstream

**File:** `river/Watershed.java` lines 109-127

The `pruneDownstream()` method matches the spec. Stops at confluences, preserves network structure. No changes needed.

### Accumulation Calculation

**File:** `river/Watershed.java` lines 301-318

The `accumulation()` method with caching matches the spec's recursive counting. No changes needed.

---

## Wiring into RiverShaping

This section describes how Watershed Building integrates into the `RiverShaping.shape()` pipeline. Watershed Building takes grouped flowlines and constructs the watershed graph.

### Current State (after Flowline Tracing)

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
    allFlowlines = new List<Flowline>()

    for region in workingSet:
        for cellPos in region.cells():
            cell = CellCache.get().getOrCompute(cellPos)
            if cell.feature() == Feature.SOURCE:
                flowline = tracer.trace(cellPos)
                if flowline.isValid():
                    allFlowlines.add(flowline)

    flowlineGroups = groupByTerminus(allFlowlines)
```

### This Implementation Adds

**Watershed Building** — After flowline grouping. Constructs watershed graph from flowlines, resolving diagonal crossings.

```
public static void shape(ChunkAccess chunk, int seaLevel):
    // ... Region Discovery, Boundary Identification, Feature Classification, Flowline Tracing unchanged ...

    flowlineGroups = groupByTerminus(allFlowlines)

    // Watershed Building — NEW
    watersheds = new List<Watershed>()
    for (terminus, flowlines) in flowlineGroups:
        watershed = new Watershed(terminus, flowlines)
        watersheds.add(watershed)

    // ... Watershed Validation, Elevation, Accumulation, Reclassification follow ...
```

**Note:** Watersheds are built but not yet stored in `WatershedCache`. Storage happens after Watershed Validation confirms the watershed is non-empty. This avoids storing watersheds that get completely pruned.

### Validation at This Stage

After this implementation, you can verify:
- Watersheds are keyed by terminus CellPos
- upstream/downstream links are correctly built
- Diagonal crossings are detected and resolved
- Multiple flowlines to same terminus produce single watershed
- Accumulation comparisons work for crossing resolution

You **cannot** yet verify:
- Short tributaries are pruned (that's Watershed Validation)
- Cells have elevation data (that's Elevation Assignment)
- Cells have width/depth data (that's Flow Accumulation)

---

## Validation Checklist

### Unit Tests Required

From the Level 4 spec:

| Test | Setup | Expected |
|------|-------|----------|
| Single flowline | One path A->B->C | downstream: {A:B, B:C, C:null} |
| Two parallel flowlines | A->B->D, C->B->D | B has two upstream (A, C) |
| Crossing current loses | Existing has higher accumulation | Current merges |
| Crossing current wins | Current has higher accumulation | Existing pruned |
| Crossing at confluence | Multiple upstream at crossing | Pruning stops |
| Multiple same terminus | Three flowlines | Single watershed |
| Identical flowlines | Same path twice | Idempotent, no duplication |

### Integration Points

After this implementation:
- Watershed Validation receives clean watersheds (graph only)
- Elevation Assignment receives validated watersheds
- Cache lookup is simple: terminus → watershed

---

## Migration Notes

### Breaking Changes

- `Watershed(RegionPos, List<Path>)` → `Watershed(CellPos, List<Flowline>)`
- `watershed.pos()` → `watershed.terminus()`
- `WatershedCache.addPath()` deleted
- `WatershedCache.finalizeReady()` deleted
- `WatershedCache.getWatershed(cell)` → use cell.terminus() then cache.getIfPresent()
- `PendingWatershed` class deleted

### Dependencies

Requires from Flowline Tracing:
- `Flowline` record with `cells()` method
- Flowlines grouped by terminus

Requires from Spatial Infrastructure:
- `Cell.withWatershed(CellPos terminus)` mutation method
- `Cell.terminus()` accessor

### Timer Updates

Update timer names:
- `Store.getTimer(Watershed.class, "constructor")` → `Store.getTimer(Watershed.class, "build")`
- `Store.getTimer(Watershed.class, "addPath")` → `Store.getTimer(Watershed.class, "addFlowline")`
