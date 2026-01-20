---
level: 5
parent: "[[River Shaping]]"
status: draft
---

# River Shaping — Implementation

This document provides implementation guidance for the [[River Shaping]] architecture specification. The spec is the source of truth; this document describes how to refactor the existing exploratory code to match.

## Scope

This implementation covers:
- Restructuring RiverShaping as explicit orchestrator
- Using RegionExplorer for working set construction
- Adding early exit for OCEANIC/INLAND regions
- Calling each pipeline stage explicitly
- Integrating Terrain Shaping and Water Placement (stubs for now)

This does NOT cover:
- Individual pipeline stage algorithms (see their respective Implementation docs)
- Terrain Shaping algorithm (spec not yet reviewed)
- Water Placement algorithm (spec not yet reviewed)

## Overview

The current RiverShaping is a minimal stub where all pipeline logic is hidden in cache `getOrCompute()` calls and constructors. The spec wants RiverShaping to be the explicit orchestrator:

| Area | Current State | Target State |
|------|---------------|--------------|
| Entry point | `generateRiverMap()` 20 lines | Explicit orchestration of all stages |
| Region discovery | D8 neighbors inline (lines 46-49) | `RegionExplorer.discover()` |
| Early exit | Not present | Return early for OCEANIC/INLAND |
| Pipeline stages | Hidden in cache callbacks | Explicit sequential calls |
| Chunk modification | Not implemented | Terrain Shaping + Water Placement |
| Neighbor selection | D8 (lines 46-49) | D4 filtered by flow direction |

## Definition of Done

**What "done" means:**

- `generateRiverMap()` explicitly calls each pipeline stage
- Uses `RegionExplorer.discover()` for working set
- Early exit for OCEANIC/INLAND center regions
- Per-region stages: Boundary Identification, Feature Classification, Flowline Tracing
- Flowlines grouped by terminus after tracing
- Per-watershed stages: Building, Validation, Elevation, Accumulation, Reclassification, Spline Building
- Terrain Shaping and Water Placement called (stubs initially)
- Unit tests for orchestration flow

**What "done" does NOT mean:**

- Terrain Shaping implementation (separate spec)
- Water Placement implementation (separate spec)

## Implementation Sequence

```
Phase 1: Restructure Entry Point
    ├── Use RegionExplorer.discover()
    ├── Add early exit for OCEANIC/INLAND
    └── Remove D8 neighbor loading

Phase 2: Per-Region Stages
    ├── Call Boundary Identification per region
    ├── Call Feature Classification per region
    └── Call Flowline Tracing per region

Phase 3: Group Flowlines
    └── Group by terminus after all regions traced

Phase 4: Per-Watershed Stages
    ├── Build watersheds from grouped flowlines
    ├── Validate each watershed
    ├── Assign elevations (parallel with accumulation)
    ├── Determine accumulation (parallel with elevation)
    ├── Reclassify features
    └── Build splines

Phase 5: Apply to Chunk
    ├── Add Terrain Shaping call (stub)
    └── Add Water Placement call (stub)
```

---

## Phase 1: Restructure Entry Point

### 1.1 Use RegionExplorer

**Current (lines 43-50):**
```
regionCache.getOrCompute(center, cellCache, sampleCache);
loadedRegions.add(center);

for (FlowDirection dir : FlowDirection.D8) {
    RegionPos neighbor = center.relative(dir);
    regionCache.getOrCompute(neighbor, cellCache, sampleCache);
    loadedRegions.add(neighbor);
}
```

**Target:**
```
workingSet = RegionExplorer.discover(chunk.getPos().getMiddleBlockPosition(0))

if workingSet.isEmpty():
    return  // early exit
```

RegionExplorer handles:
- Getting center region
- Checking if OCEANIC/INLAND (returns empty set)
- Building working set with D4 neighbors filtered by flow direction

### 1.2 Early Exit

**Current:** No early exit—always processes D8 neighbors.

**Target:** If `RegionExplorer.discover()` returns empty set, return immediately. This happens for OCEANIC (all ocean) and INLAND (no path to coast) regions.

### 1.3 Remove Side Effect Chain

The current code triggers side effects through cache lookups:
- `RegionCache.getOrCompute()` → `Region.createSkeleton()` → boundary identification
- `Region.computePaths()` → flowline tracing
- `WatershedCache.addPath()` → path grouping
- `WatershedCache.finalizeReady()` → watershed building + validation + elevation + reclassification

All of these become explicit calls in RiverShaping.

---

## Phase 2: Per-Region Stages

### 2.1 Boundary Identification

```
for region in workingSet:
    boundaries = new List<Boundary>()

    if region.type() == COASTAL:
        oceanBoundary = Oceans.boundaries(region.pos(), cellCache)
        if oceanBoundary != null:
            boundaries.add(oceanBoundary)

    if region.type() == COASTAL or region.type() == FLUVIAL:
        basinBoundary = Basins.boundaries(region.pos(), cellCache)
        if basinBoundary != null:
            boundaries.add(basinBoundary)

    enrichedRegion = region.withBoundaries(boundaries)
    RegionCache.get().put(enrichedRegion)
```

### 2.2 Feature Classification (Early Phase)

```
for region in workingSet:
    for cellPos in region.cells():
        cell = CellCache.get().getOrCompute(cellPos)
        classified = Feature.classify(cell, null)  // null watershed = early phase
        CellCache.get().put(classified)
```

Early classification identifies SOURCE and DIVIDE cells. Watershed is null because we haven't built them yet.

### 2.3 Flowline Tracing

```
allFlowlines = new List<Flowline>()
tracer = new FlowlineTracer(workingSet)  // created once, computes terminus cells internally

for region in workingSet:
    for cellPos in region.cells():
        cell = CellCache.get().getOrCompute(cellPos)
        if cell.feature() == Feature.SOURCE:
            flowline = tracer.trace(cellPos)
            if flowline.isValid():
                allFlowlines.add(flowline)
```

The tracer is created once per working set. It internally collects terminus cells from region boundaries and computes allowed regions.

---

## Phase 3: Group Flowlines

### 3.1 Group by Terminus

```
flowlineGroups = groupByTerminus(allFlowlines)

groupByTerminus(flowlines: List<Flowline>): Map<CellPos, List<Flowline>>
    groups = new Map<CellPos, List<Flowline>>()

    for flowline in flowlines:
        terminus = flowline.terminus()
        groups.computeIfAbsent(terminus, k -> new List<>()).add(flowline)

    return groups
```

---

## Phase 4: Per-Watershed Stages

### 4.1 Build, Validate, Compute Properties

```
for (terminus, flowlines) in flowlineGroups:
    // Building
    watershed = new Watershed(terminus, flowlines)

    // Validation (prunes short segments, marks cells)
    watershed.validate()

    // Skip empty watersheds after pruning (don't store)
    if watershed.allCells().isEmpty():
        continue

    // Elevation and Accumulation (can run in parallel conceptually)
    watershed.assignElevations()
    watershed.determineAccumulation()

    // Reclassification (with full watershed context)
    for cellPos in watershed.allCells():
        cell = CellCache.get().getOrCompute(cellPos)
        reclassified = Feature.classify(cell, watershed)
        CellCache.get().put(reclassified)

    // Spline Building
    watershed.buildSplines()

    // Store completed watershed
    WatershedCache.get().put(watershed)
```

Note: Watershed is stored *after* all processing, so empty watersheds are never put into the cache. No `remove()` method needed.

### 4.2 Reclassification Note

Feature Classification runs twice:
1. **Early phase** (Phase 2.2) — identifies SOURCE and DIVIDE before tracing
2. **Reclassification** (Phase 4.1) — assigns final features (Run, Waterfall, Confluence, etc.) with full watershed context

---

## Phase 5: Apply to Chunk

### 5.1 Terrain Shaping (Stub)

```
// TODO: Implement per Terrain Shaping spec
TerrainShaping.apply(chunk, cellCache, watershedCache)
```

Terrain Shaping:
- Queries cells overlapping the chunk
- Gets Shape opinions from feature handlers
- Carves river channels
- Assigns river biome

### 5.2 Water Placement (Stub)

```
// TODO: Implement per Water Placement spec
WaterPlacement.apply(chunk, cellCache, watershedCache)
```

Water Placement:
- Queries cells overlapping the chunk
- Gets Fill opinions from feature handlers
- Places water blocks with correct flow level and direction

---

## Complete Orchestration

### 5.3 Final generateRiverMap Method

```
public static void generateRiverMap(ChunkAccess chunk):
    record = Store.getTimer(RiverShaping.class, "generateRiverMap").start()

    // Phase 1: Region Discovery
    workingSet = RegionExplorer.discover(chunk.getPos().getMiddleBlockPosition(0))
    if workingSet.isEmpty():
        record.stop()
        return

    // Phase 2: Per-Region Stages
    for region in workingSet:
        // Boundary Identification
        identifyBoundaries(region)

        // Feature Classification (early)
        classifyFeatures(region, null)

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

    // Phase 3: Group by Terminus
    flowlineGroups = groupByTerminus(allFlowlines)

    // Phase 4: Per-Watershed Stages
    for (terminus, flowlines) in flowlineGroups:
        watershed = buildAndProcessWatershed(terminus, flowlines)
        if watershed != null:
            WatershedCache.get().put(watershed)

    // Phase 5: Apply to Chunk
    TerrainShaping.apply(chunk)
    WaterPlacement.apply(chunk)

    record.stop()
```

---

## Validation Checklist

### Unit Tests Required

| Test | Setup | Expected |
|------|-------|----------|
| OCEANIC early exit | Center region type OCEANIC | Returns immediately, no processing |
| INLAND early exit | Center region type INLAND | Returns immediately, no processing |
| COASTAL processes | Center region type COASTAL | Full pipeline executes |
| FLUVIAL processes | Center region type FLUVIAL | Full pipeline executes |
| Empty working set | No valid regions | Early return |
| Flowlines grouped | Multiple sources, same terminus | Single watershed with merged flowlines |
| Empty after pruning | All segments too short | Watershed removed from cache |
| Reclassification runs | Valid watershed | Cells have final features |

### Integration Points

This is the integration point. After this implementation:
- Minecraft calls `generateRiverMap()` during noise generation
- All pipeline stages execute in correct order
- Chunk is modified with river terrain and water

---

## Migration Notes

### Breaking Changes

- `generateRiverMap()` completely rewritten
- Side effect chains in caches removed
- D8 neighbor loading replaced with RegionExplorer

### Dependencies

Requires all other Implementation docs:
- RegionExplorer from [[Region Discovery Implementation]]
- Oceans/Basins from [[Boundary Identification Implementation]]
- Feature.classify() from [[Feature Classification Implementation]]
- FlowlineTracer from [[Flowline Tracing Implementation]]
- Watershed methods from [[Watershed Building Implementation]], [[Watershed Validation Implementation]], [[Elevation Assignment Implementation]], [[Flow Accumulation Implementation]]
- TerrainShaping (spec pending)
- WaterPlacement (spec pending)

### Cache Changes

Caches become simpler storage, not computation triggers:
- `RegionCache.getOrCompute()` — just fetches/stores regions
- `CellCache.getOrCompute()` — just fetches/stores cells
- `WatershedCache` — simplified per [[Watershed Building Implementation]]

### Removed Methods

- `WatershedCache.addPath()` — grouping happens in RiverShaping
- `WatershedCache.finalizeReady()` — no pending/finalized distinction
- `Region.computePaths()` — tracing happens in RiverShaping
- `Region.createSkeleton()` — boundary identification in RiverShaping

### Timer

Keep existing timer:
```
Store.getTimer(RiverShaping.class, "generateRiverMap")
```

Consider sub-timers for each phase if performance profiling needed.
