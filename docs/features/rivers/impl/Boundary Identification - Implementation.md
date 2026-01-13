---
level: 5
parent: "[[Boundary Identification]]"
status: draft
---

# Boundary Identification — Implementation Divergence

This document identifies differences between the [[Boundary Identification]] specification and the current implementation. The spec is the source of truth.

## Summary

The implementation handles ocean boundaries but lacks basin boundary support. The data structure stores boundary pairs (land cell + ocean cell) rather than the spec's consolidated list of terminus candidates.

## Data Structure Divergences

**File:** `terrain/OceanBoundary.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| `Boundary` record with `cells: List<CellPos>` and `type: BoundaryType` | `OceanBoundary` record with `land` and `ocean` fields (lines 7) | Restructure |
| `BoundaryType` enum (`OCEAN`, `BASIN`) | Missing | Add enum |
| Stores land cells only; ocean direction derived from neighbors | Stores both land and ocean cell | Store land only |

The spec consolidates all coastline cells into a single Boundary. Current implementation creates one OceanBoundary per land-ocean cell pair, leading to duplicates when a land cell borders multiple ocean cells.

## Oceans.boundaries() Divergences

**File:** `terrain/Oceans.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Returns `Boundary` or null | Returns `Set<OceanBoundary>` | Line 18 | Change return type |
| Takes `cellCache` parameter | Takes `sampleCache` | Line 18 | Use cellCache |
| Single boundary containing all land cells | One entry per land-ocean pair | Lines 38-39 | Consolidate |

Lines 34-39 create a new OceanBoundary for each land-ocean adjacency. The spec wants a single list of unique land cells.

## Missing Basin Boundary Detection

| Spec | Implementation | Action |
|------|----------------|--------|
| `Basins.boundaries()` method | Missing | Add class and method |
| Basin cells identified during Boundary Identification | Basin detection in `Path.isBasinTerminus()` lines 202-221 | Move to Boundary Identification |

Basin detection currently happens during flowline tracing (`river/Path.java` lines 202-221) rather than as a pre-identified boundary. This should be moved upstream.

## Region Integration

**File:** `region/Region.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| `boundaries: Set<Boundary>` (ocean + basin) | `Set<OceanBoundary>` only | Line 33 | Support both types |

## Dependencies

- Requires `Cell.isBasin()` from Spatial Infrastructure
- Requires `Cell.isOcean()` from Spatial Infrastructure
