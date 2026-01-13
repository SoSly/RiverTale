---
level: 5
parent: "[[Flowline Tracing]]"
status: draft
---

# Flowline Tracing — Implementation Divergence

This document identifies differences between the [[Flowline Tracing]] specification and the current implementation. The spec is the source of truth.

## Summary

The implementation captures the core tracing algorithm but has several structural and behavioral differences:
- Class named `Path` instead of `Flowline`
- Terminus cell collection uses ocean cells (wrong) instead of land boundary cells (correct)
- Nudging fallback replaced with brute-force

## Structural Divergences

**File:** `river/Path.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| `Flowline` record | `Path` class | Rename |
| `FlowlineTracer` class | Logic embedded in Path | Extract or keep combined |

## Terminus Cell Collection — Critical Bug

**File:** `river/Path.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Collect land cells from boundaries | Collects ocean cells | Lines 36-38 | Fix to use land cells |

Lines 36-38:
```
for (OceanBoundary boundary : region.boundaries()) {
    this.oceanCells.add(boundary.ocean());  // WRONG
}
```

The spec wants land cells (terminus candidates). Current implementation targets ocean cells, causing rivers to trace *into* the ocean rather than stopping at the coastline.

## Two-Mode Selection Divergence

**File:** `river/Path.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Near terminus: brute-force D8 toward terminus | Alignment scoring among flow directions | Lines 148-175 | Simplify to spec behavior |
| Far from terminus: steepest downhill with progress | Similar behavior | Lines 129-140 | Matches |

The spec's near-terminus mode (within `mergeThreshold`) ignores flow directions entirely. Current implementation at lines 148-175 (`followAlignedFlow`) still considers flow directions.

## Missing Nudge Fallback

**File:** `river/Path.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Nudge: rotate flow directions toward terminus | Brute-force D8 | Lines 177-196 | Implement nudging |

The spec's fallback rotates each flow direction toward the terminus and checks if the rotated direction makes progress. Current `forceTowardOcean()` at lines 177-196 ignores flow directions entirely.

This requires `FlowDirection.toward()` and fixed `rotateToward()` from Spatial Infrastructure.

## Basin Terminus Detection

**File:** `river/Path.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Basin cells pre-identified in boundaries | Detected mid-trace | Lines 202-221 | Remove after Boundary Identification fix |

`isBasinTerminus()` at lines 202-221 should be unnecessary once basin boundaries are properly identified upstream.

## Variable Naming

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| `terminusCells` | `oceanCells` | Line 26 | Rename |
| `findNearestTerminus()` | `findNearestOceanCell()` | Line 92 | Rename |

## Dependencies

- Requires Boundary Identification fixes (proper Boundary record, basin boundaries)
- Requires `FlowDirection.toward()` and `rotateToward()` fixes from Spatial Infrastructure
