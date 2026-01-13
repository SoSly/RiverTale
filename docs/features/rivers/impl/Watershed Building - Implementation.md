---
level: 5
parent: "[[Watershed Building]]"
status: draft
---

# Watershed Building — Implementation Divergence

This document identifies differences between the [[Watershed Building]] specification and the current implementation. The spec is the source of truth.

## Summary

The core graph-building and diagonal crossing resolution match the spec well. The main divergences are:
- Watershed keyed by RegionPos instead of terminus CellPos
- WatershedCache has complex pending/finalized lifecycle not in spec
- Cell membership tracked externally rather than on Cell record
- Constructor does too much (validation, elevation, reclassification)

## Watershed Record Divergences

**File:** `river/Watershed.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| `terminus: CellPos` field | `pos: RegionPos` field | Line 23 | Change to terminus |
| Constructor takes flowlines grouped by terminus | Takes `RegionPos` and `List<Path>` | Line 28 | Align with spec |

A region may contain multiple watersheds (different termini). Current model conflates watershed identity with region membership.

## WatershedCache Divergences

**File:** `river/WatershedCache.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Simple cache keyed by terminus | Pending/finalized split | Lines 15-18 | Simplify |
| `getOrCompute()` throws if not found | `getWatershed()` returns null | Line 86 | Change behavior |
| No PendingWatershed concept | PendingWatershed class exists | — | Remove |

The spec's model is simpler: build watershed, store via `put()`, query via `getOrCompute()`.

## Cell Membership Tracking

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Cells store terminus via `cell.withWatershed()` | External `cellToTerminus` map | Line 17 | Move to Cell record |

Current lookup at lines 86-91 uses external map. Spec stores terminus on Cell during Watershed Validation.

## Constructor Does Too Much

**File:** `river/Watershed.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Constructor builds graph only | Also prunes, assigns Y, reclassifies | Lines 37-39 | Extract to separate calls |

Lines 37-39 in constructor:
```
pruneShortTributaries();
assignYLevels();
reclassify();
```

These should be separate pipeline stages called by RiverShaping, not baked into the constructor.

## Diagonal Crossing Resolution

**File:** `river/Watershed.java`

The `detectDiagonalCrossing()` (lines 83-107) and `pruneDownstream()` (lines 109-127) methods match the spec's algorithm. No changes needed.

## terminus() Method

**File:** `river/Watershed.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| `terminus` field directly accessible | `terminus()` method traces downstream | Lines 332-342 | Add explicit field |

Current method derives terminus by tracing. Spec stores it explicitly.

## Dependencies

- Requires Cell.terminus field from Spatial Infrastructure
- Requires RiverShaping to call pipeline stages separately
