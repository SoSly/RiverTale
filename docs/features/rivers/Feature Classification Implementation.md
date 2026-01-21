---
level: 5
parent: "[[Feature Classification]]"
status: draft
---

# Feature Classification — Implementation

This document provides implementation guidance for the [[Feature Classification]] specification. The spec is the source of truth; this document describes how to refactor the existing exploratory code to match.

## Scope

This implementation covers:
- Renaming `DEFAULT` to `NONE` and updating fallthrough
- Adding `shape()`, `fill()`, and `profile()` to FeatureHandler
- Adding `Shape`, `Fill`, and `SplinePoint` records
- Fixing handler watershed membership checks
- Updating handlers for multi-sample Cell

This does NOT cover:
- Individual handler classification logic (spike documents)
- Terrain shaping implementation (see [[Terrain Shaping Implementation]])
- Water placement implementation (see [[Water Placement Implementation]])

## Overview

The classification dispatch mechanism is correct. The divergences are in interface completeness and handler implementation details:

| Area | Current State | Target State |
|------|---------------|--------------|
| Fallback feature | `DEFAULT` | `NONE` |
| FeatureHandler methods | `classify()` only | + `shape()`, `fill()`, `profile()` |
| Data structures | Missing | `Shape`, `Fill`, `SplinePoint` records |
| Watershed membership | `watershed != null` | `watershed.contains(cell.pos())` |
| Sample access | `cell.sample().depth()` | `cell.averageDepth()` |

## Definition of Done

**What "done" means:**

- Feature enum uses `NONE` instead of `DEFAULT`
- FeatureHandler has all four methods with default implementations
- Shape, Fill, SplinePoint records exist
- All handlers check watershed membership before using watershed data
- Unit tests from spec pass

**What "done" does NOT mean:**

- shape()/fill()/profile() return real data (stub implementations for now)
- All handlers have final classification logic (individual spike work)

## Implementation Sequence

```
Phase 1: Rename DEFAULT → NONE
    └── Update Feature enum and fallthrough

Phase 2: Data Structures
    ├── Add SplinePoint record
    ├── Add Shape record
    └── Add Fill record

Phase 3: FeatureHandler Interface
    ├── Add shape() method with default
    ├── Add fill() method with default
    └── Add profile() method with default

Phase 4: Handler Fixes
    ├── Add watershed membership checks
    └── Update sample access for multi-sample Cell
```

---

## Phase 1: Rename DEFAULT → NONE

### 1.1 Update Feature Enum

**File:** `cell/feature/Feature.java`

**Line 45:**
```
// Before
DEFAULT(CellType.NONE, new Default(), new float[]{0.5f, 0.5f, 0.5f, 1f});

// After
NONE(CellType.NONE, new None(), new float[]{0.5f, 0.5f, 0.5f, 1f});
```

### 1.2 Update Fallthrough

**Line 70:**
```
// Before
return cell.withFeature(DEFAULT);

// After
return cell.withFeature(NONE);
```

### 1.3 Rename Handler Class

**Rename file:** `cell/feature/Default.java` → `cell/feature/None.java`

Update class name inside the file to match.

### 1.4 Update All References

Find-and-replace across codebase:
- `Feature.DEFAULT` → `Feature.NONE`
- `new Default()` → `new None()`

Note: This rename is also mentioned in [[Spatial Infrastructure Implementation]] Phase 1.4. Coordinate to avoid duplicate work.

---

## Phase 2: Data Structures

### 2.1 SplinePoint Record

**New file:** `cell/feature/SplinePoint.java`

```
record SplinePoint(
    BlockPos pos,   // x, z from Catmull-Rom; y from profile()
    double t        // normalized position along spline (0 = entry, 1 = exit)
) {}
```

This record is used by Spline Building and passed to shape()/fill() methods.

### 2.2 Shape Record

**New file:** `cell/feature/Shape.java`

```
record Shape(
    int y,                  // target terrain surface elevation
    double weight,          // confidence 0-1 for blending
    boolean isRiverbed,     // true if underwater channel
    boolean preserveBiome,  // if true, keep original biome
    SplinePoint point       // nearest spline point (for weight calculation)
) {}
```

Returned by `FeatureHandler.shape()`. Each block position in a chunk gets a Shape opinion (or null for no opinion).

### 2.3 Fill Record

**New file:** `cell/feature/Fill.java`

```
record Fill(
    int waterY,                     // water surface level
    Integer flowLevel,              // Minecraft water level 1-7 (null = source block)
    FlowDirection flowDirection,    // downstream direction for mixin override
    double weight                   // confidence 0-1 for blending
) {}
```

Returned by `FeatureHandler.fill()`. Each block position in a chunk gets a Fill opinion (or null for no water).

---

## Phase 3: FeatureHandler Interface

### 3.1 Add Methods with Defaults

**File:** `cell/feature/FeatureHandler.java`

Expand the interface with default implementations that return null (no opinion):

```
interface FeatureHandler:
    // Existing
    classify(cell: Cell, watershed: Watershed?): boolean

    // New - terrain shaping opinions for a chunk
    default shape(cell: Cell, watershed: Watershed, chunkPos: ChunkPos): Shape[][]
        return null  // no opinion

    // New - water placement opinions for a chunk
    default fill(cell: Cell, watershed: Watershed, chunkPos: ChunkPos): Fill[][]
        return null  // no opinion

    // New - Y profile function for spline building
    default profile(t: double, entryY: int, exitY: int): int
        // Linear interpolation as default
        return (int) Math.round(entryY + t * (exitY - entryY))
```

The `default` keyword allows existing handlers to compile without implementing all methods. Handlers override these as needed.

### 3.2 Array Return Types

`shape()` and `fill()` return `[16][16]` arrays (one entry per block column in a chunk) or null.

The array is indexed `[localX][localZ]` where local coordinates are `0-15` within the chunk.

Null means "this handler has no opinion for this cell/chunk combination."

---

## Phase 4: Handler Fixes

### 4.1 Watershed Membership Check Pattern

Handlers that require watershed context must check membership before using topology:

**Bad (current):**
```
classify(cell, watershed):
    return watershed != null
```

**Good:**
```
classify(cell, watershed):
    if watershed == null:
        return false
    if not watershed.contains(cell.pos()):
        return false
    // Now safe to use watershed topology
    return true
```

### 4.2 Handlers Needing This Fix

Based on Sage's notes and code review:

| Handler | File | Issue |
|---------|------|-------|
| Run | `cell/feature/Run.java` | Only checks `watershed != null` |

Check all handlers in `cell/feature/` that use watershed. Any handler that accesses `watershed.upstream()`, `watershed.downstream()`, or `watershed.terminus()` needs the membership check.

### 4.3 Multi-Sample Cell Access

After Cell restructure (see [[Spatial Infrastructure Implementation]]), handlers should use averaging methods:

**Before:**
```
cell.sample().depth()
cell.sample().continents()
```

**After:**
```
cell.averageDepth()
cell.averageContinents()
```

**Affected handlers:**
- Spring.java line 22: uses `cell.sample().depth()`
- Any handler checking terrain density values

Search for `cell.sample()` in handler files and update to averaging methods.

### 4.4 Enriched Sample Access

For source classification fields (erosion, ridges, temperature, vegetation), handlers should use:

```
cell.averageErosion()
cell.averageRidges()
cell.averageTemperature()
cell.averageVegetation()
```

These methods handle lazy enrichment internally. If a sample hasn't been enriched for the requested field, the averaging method enriches it, updates SampleCache, then computes the average. Handlers don't need to manage enrichment—just call the averaging method.

### 4.5 Reset Broken Handlers to Stubs

Several handlers use APIs that break after Spatial Infrastructure refactoring. Don't migrate the exploratory logic — reset them to stubs that return `false`. Individual spike docs will implement them properly.

**Handlers using `cell.sample()` (becomes `cell.samples()`):**
- Spring.java
- Resurgence.java
- Seep.java
- Snowmelt.java
- Divide.java

**Handlers using `Cell.hasUpstreamNeighbor()` (static method being removed):**
- Spring.java

**Stub implementation:**
```
classify(cell, watershed):
    return false  // TODO: implement per spike doc
```

This is intentional. The exploratory classification logic was for testing; the final logic will come from spike documents that define each feature's criteria properly.

### 4.6 Add Cell.hasInflowingNeighbor()

All source handlers need to check whether a cell has any D8 neighbor flowing toward it. Add this method to Cell:

```
hasInflowingNeighbor():
    for dir in FlowDirection.D8:
        neighborPos = pos.relative(dir)
        neighbor = CellCache.get().getOrCompute(neighborPos)

        if neighbor.flowDirections().contains(dir.opposite()):
            return true

    return false
```

This replaces the old static `Cell.hasUpstreamNeighbor()` method with an instance method. If any neighbor flows toward this cell, it's not a true source — water is already arriving from upstream.

---

## Wiring into RiverShaping

This section describes how Feature Classification integrates into the `RiverShaping.shape()` pipeline. Feature Classification has two insertion points: early classification (before tracing) and reclassification (after watershed stages).

### Current State (after Boundary Identification)

```
public static void shape(ChunkAccess chunk, int seaLevel):
    // Region Discovery
    workingSet = RegionExplorer.discover(chunk.getPos().getMiddleBlockPosition(0))
    if workingSet.isEmpty():
        return

    // Boundary Identification
    for region in workingSet:
        boundaries = switch region.type():
            case COASTAL -> Coastal.boundaries(region.pos(), cellCache)
            case FLUVIAL -> Fluvial.boundaries(region.pos(), cellCache)
            default -> List.of()

        enriched = region.withBoundaries(boundaries)
        RegionCache.get().put(enriched)
```

### This Implementation Adds

**Early Classification** — After boundaries, before tracing. Identifies SOURCE and DIVIDE cells so Flowline Tracing knows where to start.

```
public static void shape(ChunkAccess chunk, int seaLevel):
    // Region Discovery
    workingSet = RegionExplorer.discover(...)
    if workingSet.isEmpty():
        return

    // Boundary Identification
    for region in workingSet:
        // ... unchanged ...

    // Feature Classification (early phase) — NEW
    for region in workingSet:
        for cellPos in region.cells():
            cell = CellCache.get().getOrCompute(cellPos)
            classified = Feature.classify(cell, null)  // null = no watershed yet
            CellCache.get().put(classified)

    // Reclassification placeholder — added now, called after watershed stages
    // See below for where this gets called
```

**Reclassification** — After watershed stages complete (Validation, Elevation, Accumulation). Future implementation docs will slot their stages between early classification and reclassification.

```
    // ... after Flowline Tracing, Watershed Building, Validation, Elevation, Accumulation ...

    // Feature Reclassification (with watershed context) — NEW
    for watershed in watersheds:
        for cellPos in watershed.allCells():
            cell = CellCache.get().getOrCompute(cellPos)
            reclassified = Feature.classify(cell, watershed)
            CellCache.get().put(reclassified)

    // ... Spline Building, Terrain Shaping, Water Placement follow ...
```

### Validation at This Stage

After this implementation, you can verify:
- Cells in the working set have features assigned
- SOURCE cells exist (requires at least one working source handler)
- DIVIDE cells exist at ridgelines
- Cells outside working set remain NONE

You **cannot** yet verify:
- Reclassification (no watersheds to reclassify with)
- Downstream features like RUN, WATERFALL, CONFLUENCE (require watershed context)

---

## Validation Checklist

### Unit Tests Required

From the Level 4 spec:

| Test | Setup | Expected |
|------|-------|----------|
| Early phase with null | classify(cell, null) | Only SOURCE/DIVIDE/NONE match |
| Reclassify with watershed | classify(cell, watershed) | Full handler set evaluated |
| Enum order sources | Cell matching multiple sources | First matching source wins |
| Enum order courses | Cell with 10-block drop | WATERFALL, not RAPIDS |
| NONE fallback | Cell matching no handlers | Feature.NONE assigned |
| Handler returns false early | Needs watershed but null | Returns false, no exception |
| Cell not in watershed | Cell outside provided watershed | Returns false |

### Integration Points

After this implementation:
- Flowline Tracing can query cell features
- Watershed Building can check SOURCE cells
- Terrain Shaping can call shape() (stubs for now)
- Water Placement can call fill() (stubs for now)

---

## Migration Notes

### Breaking Changes

- `Feature.DEFAULT` removed, use `Feature.NONE`
- `Default.java` renamed to `None.java`
- `FeatureHandler` gains three new methods (with defaults, so handlers compile)

### Dependencies

Requires from Spatial Infrastructure:
- `Cell.averageDepth()`, `Cell.averageContinents()`, etc.
- `Cell.withFeature(Feature)` mutation method

Requires for downstream:
- Watershed class with `contains(CellPos)` method
- ChunkPos for shape()/fill() array sizing

### Stub vs Real Implementation

The shape()/fill()/profile() methods are stubs in this phase. Real implementations come with:
- Terrain Shaping Implementation (shape methods)
- Water Placement Implementation (fill methods)
- Spline Building Implementation (profile methods)

The interface is defined here so handlers can be extended incrementally.

### RandomState Access

The Crater handler requires position-seeded randomness for its probability check. Rather than passing `RandomState` through method signatures, we store it on `RiverShaping` alongside `seaLevel`:

- `RiverShaping.shape()` accepts `RandomState` from the mixin and stores it statically
- Handlers access it via `RiverShaping.getRandomState()`
- The value is cleared in `RiverShaping.shutdown()`

This mirrors the existing pattern for `seaLevel` and keeps handler method signatures simple.
