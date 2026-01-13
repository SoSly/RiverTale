---
level: 5
parent: "[[Region Discovery]]"
status: draft
---

# Region Discovery — Implementation Divergence

This document identifies differences between the [[Region Discovery]] specification and the current implementation. The spec is the source of truth.

## Summary

Region Discovery logic is scattered across multiple classes rather than centralized in a dedicated `RegionExplorer`. The implementation uses D8 neighbor traversal instead of D4, and lacks the border cell flow direction checks that filter irrelevant neighbors.

## Structural Divergences

| Spec | Implementation | Action |
|------|----------------|--------|
| Dedicated `RegionExplorer` class | Missing; logic in `Region.java` lines 82-111 | Create RegionExplorer |
| Entry point: `RegionExplorer.discover(blockPos)` | Entry via `Region.buildTraceRegions()` line 82 | Create public API |

## Algorithm Divergences

**File:** `region/Region.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| D4 only for region neighbor traversal | D8 neighbors | Line 103: `FlowDirection.D8` | Change to D4 |
| Border cell flow direction checks | Not implemented | — | Add filtering |
| Early exit for OCEANIC/INLAND at discovery | Exit in `CellCache` line 79 and `Region.tracePaths()` line 56 | Move to discovery |

### D8 vs D4 Issue

Line 103 in `Region.java`:
```
for (FlowDirection dir : FlowDirection.D8) {
```

The spec requires D4 only. Rivers cross region boundaries cardinally, not diagonally. FLUVIAL is defined as "D4-adjacent to COASTAL."

### Missing Border Cell Flow Checks

The spec filters neighbors by checking if border cells actually flow toward the target region. Current implementation at lines 102-110 only checks neighbor type, not flow direction.

## RiverShaping Integration

**File:** `world/RiverShaping.java`

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Calls `RegionExplorer.discover()` | Inline D8 iteration | Lines 46-49 | Use RegionExplorer |
| Returns filtered working set | Loads all 9 regions | Lines 43-50 | Filter properly |
| Early return for OCEANIC/INLAND | No early return | — | Add early exit |

Lines 43-50 load center + 8 D8 neighbors unconditionally. The spec filters to center + relevant D4 neighbors only, and returns early for ocean/inland chunks.

## Dependencies

- Requires `RegionPos.getBorderCells(dir)` from Spatial Infrastructure
- Requires flow direction checks against border cells
