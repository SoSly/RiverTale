---
level: 5
parent: "[[Watershed Validation]]"
status: draft
---

# Watershed Validation — Implementation Divergence

This document identifies differences between the [[Watershed Validation]] specification and the current implementation. The spec is the source of truth.

## Summary

The pruning algorithm matches the spec well. The main divergence is that validation runs inside the Watershed constructor rather than as a separate orchestrated step, and cell membership marking uses an external map rather than the Cell record.

## Structural Divergences

**File:** `river/Watershed.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Separate `validate()` method called by RiverShaping | `pruneShortTributaries()` in constructor | Line 37 | Extract to public method |
| Cell marking via `cell.withWatershed()` | Marking via `WatershedCache.cellToTerminus` | `WatershedCache.java` line 81 | Move to Cell |

## Algorithm — Matches Spec

The pruning algorithm at lines 129-146 matches the spec:
- Iterates until no pruning occurs (line 133)
- Finds sources (line 135)
- Traces to confluence or terminus (line 138)
- Prunes segments shorter than minPathLength (lines 139-142)

No algorithm changes needed.

## Cell Membership Marking

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Mark cells after pruning via `cell.withWatershed()` | Mark in `WatershedCache.finalizeWatershed()` | `WatershedCache.java` lines 80-83 | Move to validation |

Spec marks cells during validation so only surviving cells have terminus set. Current implementation marks in WatershedCache after construction.

## Configuration

**File:** `config/CommonConfig.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| `minPathLength` default 5, range 1-32 | Default 3, range 1-20 | Lines 20, 87 | Align with spec |

## Dependencies

- Requires Cell.terminus field from Spatial Infrastructure
- Requires RiverShaping to call validate() after building
