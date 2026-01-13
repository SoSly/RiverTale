---
level: 5
parent: "[[Flow Accumulation]]"
status: draft
---

# Flow Accumulation — Implementation Divergence

This document identifies differences between the [[Flow Accumulation]] specification and the current implementation. The spec is the source of truth.

## Summary

Flow Accumulation is **largely unimplemented**. The implementation has a basic `accumulation()` method that counts upstream cells, but lacks width/depth computation and the required Cell fields.

## Missing Features

| Spec | Implementation | Action |
|------|----------------|--------|
| `width: int` on Cell | Missing | Add field |
| `depth: int` on Cell | Missing | Add field |
| `upstreamCount: int` on Cell | Missing | Add field |
| `downstreamCount: int` on Cell | Missing | Add field |
| `determineAccumulation()` pipeline step | Not present | Add method |
| Width formula (log saturation + variance) | Not implemented | Implement |
| Depth formula (log saturation from confluences) | Not implemented | Implement |

## Existing accumulation() Method

**File:** `river/Watershed.java`

Lines 301-318 implement basic upstream cell counting:

| Spec | Implementation | Location | Action |
|------|----------------|----------|--------|
| Returns count, stores width/depth on Cell | Returns count only, doesn't store | Lines 301-318 | Extend |

This method is used for diagonal crossing resolution. It's a building block but not the full spec.

## Missing Configuration Parameters

**File:** `config/CommonConfig.java`

| Parameter | Spec Default | Status |
|-----------|--------------|--------|
| `minWidth` | 3 | Missing |
| `maxWidth` | 24 | Missing |
| `widthScale` | 3.0 | Missing |
| `variancePercent` | 0.2 | Missing |
| `minDepth` | 2 | Missing |
| `maxDepth` | 8 | Missing |
| `depthScale` | 2.0 | Missing |

## Missing Cell Fields

**File:** `cell/Cell.java`

| Field | Purpose | Status |
|-------|---------|--------|
| `width` | River width in blocks | Missing |
| `depth` | Channel depth in blocks | Missing |
| `upstreamCount` | Immediate upstream neighbor count | Missing |
| `downstreamCount` | Immediate downstream neighbor count (0 or 1) | Missing |

## Missing Mutation Method

**File:** `cell/Cell.java`

| Spec | Implementation | Action |
|------|----------------|--------|
| `withAccumulation(width, depth, upstreamCount, downstreamCount)` | Missing | Add method |

## Dependencies

- Requires Cell fields from Spatial Infrastructure
- Requires configuration parameters in CommonConfig
- Requires world seed access for seeded variance
