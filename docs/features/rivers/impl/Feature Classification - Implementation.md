---
level: 5
parent: "[[Feature Classification]]"
status: draft
---

# Feature Classification — Implementation Divergence

This document identifies differences between the [[Feature Classification]] specification and the current implementation. The spec is the source of truth.

## Summary

The core classification dispatch mechanism matches the spec. The main divergences are:
- Missing `shape()`, `fill()`, and `profile()` methods on FeatureHandler
- Fallback feature named `DEFAULT` instead of `NONE`
- Feature handlers don't check watershed membership

## FeatureHandler Interface Divergences

**File:** `cell/feature/FeatureHandler.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| `classify(cell, watershed): boolean` | Line 8: Present | Matches |
| `shape(cell, watershed, chunkPos): Shape[16][16]?` | Missing | Add method |
| `fill(cell, watershed, chunkPos): Fill[16][16]?` | Missing | Add method |
| `profile(t, entryY, exitY): int` | Missing | Add method |

## Missing Data Structures

| Spec | Implementation | Action |
|------|----------------|--------|
| `Shape` record | Missing | Add record |
| `Fill` record | Missing | Add record |
| `SplinePoint` record | Missing | Add record |

These records are outputs of FeatureHandler methods, consumed by Terrain Shaping and Water Placement.

## Feature Enum Divergences

**File:** `cell/feature/Feature.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Fallback feature named `NONE` | Named `DEFAULT` | Line 45 | Rename |
| Falls through to `NONE` | Falls through to `DEFAULT` | Line 70 | Update after rename |

## Handler Implementation Issues

### Run Handler

**File:** `cell/feature/Run.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Check `watershed.contains(cell.pos())` | Only checks `watershed != null` | Line 9 | Add membership check |

Line 9 returns true for any cell if watershed is non-null, even if the cell isn't in that watershed.

### Spring Handler

**File:** `cell/feature/Spring.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Use `cell.averageDepth()` with multi-sample | Uses `cell.sample().depth()` | Line 22 | Update after Cell restructure |

## CellType Alignment

**File:** `cell/CellType.java`

All CellType values match spec: SOURCE, TERMINUS, JUNCTION, LAKE, COURSE, NONE.

## Dependencies

- Shape/Fill records depend on Cell having entry/exit Y (see Spatial Infrastructure)
- `profile()` depends on spline infrastructure (see Spline Building)
