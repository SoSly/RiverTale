---
level: 5
parent: "[[Spatial Infrastructure]]"
status: draft
---

# Spatial Infrastructure — Implementation Divergence

This document identifies differences between the [[Spatial Infrastructure]] specification and the current implementation. The spec is the source of truth.

## Summary

The implementation has the basic coordinate types and caching infrastructure, but diverges significantly on:
- **Cell data model**: Single sample vs. multi-sample; single Y vs. entry/exit Y; missing river properties
- **Sample lifecycle**: All fields sampled at once vs. two-tier lazy sampling
- **Configuration units**: Blocks vs. chunks/cells
- **Missing methods**: Several coordinate helper methods not implemented

## CellPos Divergences

**File:** `core/CellPos.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| `contains(pos: BlockPos): boolean` | Missing | Add method |

## RegionPos Divergences

**File:** `core/RegionPos.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| `containsOutOfBoundsCells(): boolean` | Missing | Add method |
| `getBorderCells(dir: FlowDirection): List<CellPos>` | Missing | Add method |
| `contains(pos: BlockPos): boolean` | Missing | Add method |

## FlowDirection Divergences

**File:** `core/FlowDirection.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| `toward(from: CellPos, to: CellPos): FlowDirection` | Missing | Add static method |
| `rotateToward(target)` rotates one step (45°) toward target | Lines 40-58: Rotates by fixed 90° (2 compass indices) | Fix algorithm |

## Sample Divergences

**File:** `density/Sample.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| Two-tier sampling (core fields on miss, others lazy) | Lines 7-15: All 6 fields in record | Refactor to two-tier |
| `hasFullDensities(): boolean` | Missing | Add method |
| `withFullDensities(): Sample` | Missing | Add method |
| `estimatedTerrainHeight(): int` | Line 24: Named `estimatedHeight()` | Rename |

## Cell Divergences — Critical

**File:** `cell/Cell.java`

The Cell record has the most significant divergences.

| Spec | Implementation | Action |
|------|----------------|--------|
| `samples: Set<Sample>` (1 to CellSize² samples) | Line 16: `sample: Sample` (single) | Refactor to multi-sample |
| `entryY: int`, `exitY: int` (boundary elevations) | Line 16: `y: int` (single value) | Replace with entry/exit |
| `waypoint: BlockPos` (lazy computed) | Missing | Add field |
| `width: int`, `depth: int` | Missing | Add fields |
| `upstreamCount: int`, `downstreamCount: int` | Missing | Add fields |
| `terminus: CellPos` (watershed lookup key) | Missing | Add field |
| `entry_t: double`, `exit_t: double` | Missing | Add fields |
| `averageContinents()`, `averageDepth()`, etc. | Missing | Add averaging methods |
| `averageEstimatedTerrainHeight()` | Missing | Add method |
| `isOcean(): boolean` | Missing on Cell (Sample has it at line 16) | Add method |
| `isBasin(): boolean` | Missing | Add method |
| `withFlowDirections()` | Missing | Add mutation method |
| `withWaypoint()` | Missing | Add mutation method |
| `withWatershed()` | Missing | Add mutation method |
| `withElevations(entryY, exitY)` | Line 65: Has `withY(newY)` instead | Replace |
| `withAccumulation(width, depth, upstreamCount, downstreamCount)` | Missing | Add mutation method |
| `withSplineParams(entry_t, exit_t)` | Missing | Add mutation method |

## CellCache Divergences

**File:** `cell/CellCache.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| Flow directions computed lazily on first request | Line 85: Computed during `getOrCompute()` | Make lazy |
| Sample positions via greedy farthest-point algorithm | Line 74: Single center sample only | Implement multi-sample |
| Classification is downstream concern | Line 86: `Feature.classify()` called in cache | Remove from cache |

## SampleCache Divergences

**File:** `density/SampleCache.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| Two-tier sampling (core fields first) | Line 67: All fields sampled via `sampler.sample()` | Implement two-tier |

## RegionCache Divergences

**File:** `region/RegionCache.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| Returns Region with type and boundaries only | Line 74: Triggers `computePaths()` during cache miss | Remove path computation |

## Configuration Divergences

**File:** `config/CommonConfig.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| `CellSize` in chunks (default 3) | Line 16: `cellSize` in blocks (default 48) | Change unit |
| `RegionSize` in cells (default 32) | Line 15: `regionSize` in blocks (default 1536) | Change unit |
| `SamplesPerCell` (default 1, range 1-CellSize²) | Missing | Add parameter |
| Width parameters (`minWidth`, `maxWidth`, `widthScale`, `variancePercent`) | Missing | Add parameters |
| Depth parameters (`minDepth`, `maxDepth`, `depthScale`) | Missing | Add parameters |

## Missing Infrastructure

| Spec Component | Status |
|----------------|--------|
| `Boundary` record with `cells: List<CellPos>` and `type: BoundaryType` | Missing |
| `BoundaryType` enum (`OCEAN`, `BASIN`) | Missing |
| World boundary checks via `containsOutOfBoundsCells()` | Missing |

## Implementation Priority

1. **Cell restructure** — Most critical; downstream specs depend on entry/exit Y, width/depth, terminus reference.
2. **Configuration units** — Changes config interpretation everywhere; do early.
3. **Multi-sample support** — Can defer if SamplesPerCell defaults to 1.
4. **Two-tier sampling** — Performance optimization; can defer.
5. **Missing methods on coordinate types** — Add as needed by downstream specs.
