---
level: 5
parent: "[[River Shaping]]"
status: draft
---

# River Shaping — Implementation Divergence

This document identifies differences between the [[River Shaping]] specification and the current implementation. The spec is the source of truth.

## Summary

The current `RiverShaping` class is a minimal stub. All pipeline logic is hidden inside cache `getOrCompute()` calls and constructors. The spec wants RiverShaping to be the explicit orchestrator that drives each pipeline stage in sequence.

## Current State

**File:** `world/RiverShaping.java`

The entire implementation is 55 lines. The `generateRiverMap()` method at lines 34-54:
- Computes center region position (line 36)
- Calls `RegionCache.getOrCompute()` for center + 8 D8 neighbors (lines 43-49)
- Calls `WatershedCache.finalizeReady()` (line 52)
- Returns without modifying the chunk

Everything happens as side effects of cache lookups.

## Missing Orchestration

| Spec Stage | Implementation | Location | Action |
|------------|----------------|----------|--------|
| Region Discovery (explicit call) | Hidden in cache callbacks | — | Add explicit call |
| Early exit for OCEANIC/INLAND | Not present | — | Add early return |
| Per-region boundary identification | In `Region.createSkeleton()` | `Region.java` line 46-50 | Move to RiverShaping |
| Per-region feature classification | In `CellCache.getOrCompute()` | `CellCache.java` line 86 | Move to RiverShaping |
| Per-region flowline tracing | In `Region.computePaths()` | `Region.java` lines 55-79 | Move to RiverShaping |
| Group flowlines by terminus | In `WatershedCache.addPath()` | `WatershedCache.java` lines 45-58 | Move to RiverShaping |
| Per-watershed building | In `Watershed` constructor | `Watershed.java` line 28 | Call explicitly |
| Per-watershed validation | In `Watershed` constructor | `Watershed.java` line 37 | Call explicitly |
| Per-watershed elevation | In `Watershed` constructor | `Watershed.java` line 38 | Call explicitly |
| Per-watershed accumulation | Not implemented | — | Add |
| Per-watershed reclassification | In `Watershed` constructor | `Watershed.java` line 39 | Call explicitly |
| Per-watershed spline building | Not implemented | — | Add |
| Terrain Shaping | Not implemented | — | Add |
| Water Placement | Not implemented | — | Add |

## Cascade of Side Effects

Current flow when `generateRiverMap()` runs:

1. `RegionCache.getOrCompute()` (line 43)
2. → `Region.createSkeleton()` → boundary identification
3. → `Region.computePaths()` (line 74 of RegionCache)
4. → `Path.trace()` for each source
5. → `WatershedCache.addPath()` for each valid path
6. `WatershedCache.finalizeReady()` (line 52)
7. → `Watershed` constructor → building + validation + elevation + reclassification

The spec wants these as explicit sequential calls in RiverShaping.

## Missing Chunk Modification

**File:** `world/RiverShaping.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| Terrain Shaping modifies chunk blocks | Not implemented | Add |
| Water Placement fills channels | Not implemented | Add |
| Biome assignment | Not implemented | Add |

The method receives a `ChunkAccess` parameter (line 34) but never uses it. Rivers are computed but not applied.

## D8 vs D4 Neighbor Loading

**File:** `world/RiverShaping.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Load D4 neighbors via RegionExplorer | Loads D8 neighbors inline | Lines 46-49 | Use RegionExplorer |

Lines 46-49 iterate `FlowDirection.D8`. Spec uses D4 only and filters by flow direction.

## Dependencies

RiverShaping is the integration point. It depends on all other specs:
- RegionExplorer from Region Discovery
- Boundary/Basins from Boundary Identification
- Feature.classify() from Feature Classification
- FlowlineTracer from Flowline Tracing
- Watershed methods: build, validate, assignElevations, determineAccumulation, reclassify, buildSplines
- Terrain Shaping spec (not reviewed)
- Water Placement spec (not reviewed)
