---
level: 5
parent: "[[Elevation Assignment]]"
status: draft
---

# Elevation Assignment — Implementation Divergence

This document identifies differences between the [[Elevation Assignment]] specification and the current implementation. The spec is the source of truth.

## Summary

The implementation has the basic downstream-then-upstream pattern, but uses a single center Y value instead of the spec's boundary-based entry/exit model. This is a fundamental data model difference.

## Data Model Divergence — Critical

**File:** `cell/Cell.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| `entryY: int` (upstream boundary) | Missing | — | Add field |
| `exitY: int` (downstream boundary) | Missing | — | Add field |
| Constraint: `upstream.exitY == downstream.entryY` | Not applicable | — | Implement |
| Single `y: int` | Line 16 | — | Replace with entry/exit |

## Exit Point Sampling — Missing

**File:** `river/Watershed.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Sample terrain at exit point toward downstream | Uses cell center sample | Line 235 | Add exit point estimation |

Line 235 uses `cell.sample().estimatedHeight()` which samples at cell center. Spec samples at the boundary crossing point.

## Algorithm Divergences

**File:** `river/Watershed.java`

### Downstream Pass

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Follow terrain, allow below sea level | Clamps to seaLevel during pass | Line 239, 247 | Remove sea level clamp |

Lines 239 and 247 use `Math.max(seaLevel, ...)` during the downstream pass. Spec allows going below sea level; the upstream pass fixes it.

### Upstream Pass

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Raise terminus to seaLevel - 1, propagate up | Raises based on minSlope from downstream | Lines 254-278 | Align with spec |

The upstream pass at lines 254-278 adjusts based on minSlope constraint from downstream cells. Spec explicitly sets terminus to seaLevel - 1 first, then propagates.

## Confluence Handling

**File:** `river/Watershed.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Take min exitY, adjust higher tributaries | Constrains current cell from upstream | Lines 241-246 | Adjust upstream cells |

Lines 241-246 constrain the current cell's Y based on upstream values, but don't adjust upstream cells to share a common exit elevation.

## Mutation Methods

**File:** `cell/Cell.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| `withElevations(entryY, exitY)` | `withY(newY)` | Line 65 | Replace |

## Dependencies

- Requires Cell entry/exit fields from Spatial Infrastructure
- Requires `cell.withElevations()` method
- Requires waypoint infrastructure for exit point estimation
