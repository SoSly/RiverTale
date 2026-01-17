---
level: 5
parent: "[[Boundary Identification]]"
status: draft
---

# Boundary Identification — Implementation

This document provides implementation guidance for the [[Boundary Identification]] specification. The spec is the source of truth; this document describes how to refactor the existing exploratory code to match.

## Scope

This implementation covers:
- Restructuring `OceanBoundary` → `Boundary` record
- Refactoring `Oceans` to return consolidated boundaries
- Creating the `Basins` class for basin detection
- Updating `Region` to store both boundary types

This does NOT cover:
- Boundary/BoundaryType definitions (see [[Spatial Infrastructure Implementation]])
- Flowline tracing to boundaries (see [[Flowline Tracing Implementation]])

## Overview

The current code handles ocean boundaries but diverges from the spec in data structure and lacks basin support:

| Area | Current State | Target State |
|------|---------------|--------------|
| Data structure | `OceanBoundary(land, ocean)` pairs | `Boundary(List<CellPos>, BoundaryType)` |
| Ocean detection | One entry per land-ocean pair | Single boundary with all land cells |
| Return type | `Set<OceanBoundary>` | `Boundary` or null |
| Cache used | `SampleCache` | `CellCache` |
| Basin detection | In `Path.isBasinTerminus()` during tracing | Dedicated `Basins` class |
| Region storage | `Set<OceanBoundary>` | `List<Boundary>` |

## Definition of Done

**What "done" means:**

- `OceanBoundary` class deleted
- `Oceans.boundaries()` returns `Boundary` or null
- `Basins` class exists with `boundaries()` method
- `Region` stores `List<Boundary>` via `withBoundaries()`
- Unit tests from spec pass
- `/rivertale boundaries` command works

**What "done" does NOT mean:**

- Flowlines trace to these boundaries (that's Flowline Tracing)
- Rivers actually generate (downstream specs)

## Implementation Sequence

```
Phase 1: Data Structure
    ├── Delete terrain/OceanBoundary.java
    └── Update Region to use List<Boundary>

Phase 2: Oceans Refactor
    ├── Change parameter from SampleCache to CellCache
    ├── Change return type to Boundary (or null)
    └── Consolidate land cells into single list

Phase 3: Basins Class
    ├── Create terrain/Basins.java
    └── Implement boundaries() method

Phase 4: Integration
    ├── Add Region.withBoundaries() mutation
    ├── Update RiverShaping to call both detectors
    └── Remove basin detection from Path.java

Phase 5: Commands
    └── Add /rivertale boundaries command
```

---

## Phase 1: Data Structure

### 1.1 Delete OceanBoundary

**Delete file:** `terrain/OceanBoundary.java`

The `bearing()` method functionality is no longer needed — the spec says ocean direction is derived from D4 neighbors when needed, not stored.

The `encode()`/`decode()` methods are replaced by `Boundary.encode()`/`decode()` (defined in Spatial Infrastructure).

### 1.2 Convert Region to Record

**File:** `region/Region.java`

Region is currently a mutable class with `void addBoundary()`. Convert it to an immutable record like Cell.

**Current structure:**
```
class Region:
    pos: RegionPos (final)
    type: RegionType (final)
    boundaries: Set<OceanBoundary> (mutable)

    void addBoundary(OceanBoundary)
```

**Target structure:**
```
record Region(
    RegionPos pos,
    RegionType type,
    List<Boundary> boundaries
)
```

With mutation method:
```
withBoundaries(boundaries: List<Boundary>): Region
    return new Region(pos, type, boundaries)
```

This is a larger refactor than just changing the field type. The constructor, all fields, and mutation pattern change. All code that calls `addBoundary()` needs to use `withBoundaries()` instead.

---

## Phase 2: Oceans Refactor

### 2.1 Change Signature

**File:** `terrain/Oceans.java`

**Current (line 18):**
```
static boundaries(region: RegionPos, sampleCache: SampleCache): Set<OceanBoundary>
```

**Target:**
```
static boundaries(region: RegionPos, cellCache: CellCache): Boundary
```

Returns `Boundary` or `null` (not an empty set).

### 2.2 Use CellCache

The current code queries `SampleCache` directly:
```
sample = sampleCache.getOrCompute(cellPos.getMiddleBlockX(), cellPos.getMiddleBlockZ())
if sample.isOcean():
    ...
```

The spec wants us to use `CellCache`:
```
cell = cellCache.getOrCompute(cellPos)
if cell.isOcean():
    ...
```

This delegates to the Cell's averaged sample data rather than sampling a single point.

### 2.3 Consolidate Land Cells

**Current behavior (lines 34-40):**
Creates one `OceanBoundary` per land-ocean pair. If a land cell borders two ocean cells, it gets two entries.

**Target behavior:**
Collect unique land cells into a single list. Each land cell appears once regardless of how many ocean neighbors it has.

```
boundaries(region: RegionPos, cellCache: CellCache): Boundary
    landCells = new List<CellPos>()
    minCell = region.getMinCell()
    cells = cellsPerRegion()

    for x in 0 to cells-1:
        for z in 0 to cells-1:
            cellPos = CellPos(minCell.x + x, minCell.z + z)
            cell = cellCache.getOrCompute(cellPos)

            if cell.isOcean():
                continue

            // Check if any D4 neighbor is ocean
            for dir in D4:
                neighborPos = cellPos.relative(dir)
                neighbor = cellCache.getOrCompute(neighborPos)

                if neighbor.isOcean():
                    landCells.add(cellPos)
                    break  // Only add once per land cell

    if landCells.isEmpty():
        return null

    return Boundary(landCells, OCEAN)
```

**Cell ordering:** Cells are added in x/z iteration order (row by row), not geographic order (clockwise around coastline). This is intentional — Flowline Tracing uses boundaries as terminus candidates and only needs to check membership, not walk the coastline in sequence. If geographic ordering is ever needed for visualization, that's a future enhancement.

---

## Phase 3: Basins Class

### 3.1 Create Class

**New file:** `terrain/Basins.java`

```
class Basins:
    private Basins()  // utility class

    static boundaries(region: RegionPos, cellCache: CellCache): Boundary
        timer = Store.getTimer(Basins.class, "boundaries").start()

        basinCells = new List<CellPos>()
        minCell = region.getMinCell()
        cells = cellsPerRegion()

        for x in 0 to cells-1:
            for z in 0 to cells-1:
                cellPos = CellPos(minCell.x + x, minCell.z + z)
                cell = cellCache.getOrCompute(cellPos)

                if cell.isBasin():
                    basinCells.add(cellPos)

        timer.stop()

        if basinCells.isEmpty():
            return null

        return Boundary(basinCells, BASIN)
```

### 3.2 Cell.isBasin() Dependency

This method is defined in Spatial Infrastructure:
```
isBasin(): boolean
    if isOcean():
        return false
    seaLevel = 63
    return samples.values().stream()
        .allMatch(s -> s.estimatedTerrainHeight() < seaLevel)
```

Basin cells are land cells where ALL samples are below sea level. A cell with mixed samples above/below is not a basin.

---

## Phase 4: Integration

### 4.1 Add Region.withBoundaries()

**File:** `region/Region.java`

Add mutation method:
```
withBoundaries(boundaries: List<Boundary>): Region
    return new Region(pos, type, boundaries, ...)
```

### 4.2 Update RiverShaping

**File:** `world/RiverShaping.java`

After `RegionExplorer.discover()`, call boundary identification for each region:

```
for region in workingSet:
    boundaries = new List<Boundary>()

    if region.type() == COASTAL:
        oceanBoundary = Oceans.boundaries(region.pos(), cellCache)
        if oceanBoundary != null:
            boundaries.add(oceanBoundary)

    if region.type() == COASTAL or region.type() == FLUVIAL:
        basinBoundary = Basins.boundaries(region.pos(), cellCache)
        if basinBoundary != null:
            boundaries.add(basinBoundary)

    enrichedRegion = region.withBoundaries(boundaries)
    // Update working set or cache with enriched region
```

### 4.3 Remove Basin Detection from Path

**File:** `river/Path.java`

Sage notes basin detection currently happens in `isBasinTerminus()` at lines 202-221. This logic moves to `Basins.boundaries()`.

Review `Path.java` and remove or simplify `isBasinTerminus()`. Flowline Tracing should check if a cell is in a basin boundary, not recompute basin status.

**Semantic change:** The old `isBasinTerminus()` checked two conditions:
1. Cell is below sea level
2. Cell is a local minimum (no lower D8 neighbors)

The new `Cell.isBasin()` only checks condition 1. This is intentional — all below-sea-level cells are valid terminus candidates, not just local minima. The local-minimum logic is removed entirely. This simplifies the algorithm and lets Flowline Tracing pick any basin cell as a terminus.

---

## Phase 5: Commands

### 5.1 Add /rivertale boundaries Command

**New file:** `command/BoundariesCommand.java`

```
/rivertale boundaries <x> <z>
```

Output format:
```
Boundaries for RegionPos(0, 0) - COASTAL:

  Ocean boundary: 47 cells
    Northwest corner: CellPos(0, 0)
    Southeast corner: CellPos(31, 12)

  Basin boundary: 3 cells
    CellPos(15, 20)
    CellPos(15, 21)
    CellPos(16, 20)
```

### 5.2 Add /rivertale vis boundaries Command

Spawn particles at boundary cells for visual debugging:
- Blue particles for ocean boundary cells
- Green particles for basin boundary cells

---

## Validation Checklist

### Unit Tests Required

From the Level 4 spec:

| Test | Setup | Expected |
|------|-------|----------|
| Ocean null when all ocean | COASTAL, all cells ocean | null |
| Ocean null when all land | COASTAL, all cells land, no ocean neighbors | null |
| Ocean at transition | COASTAL with land/ocean divide | Boundary with coastline cells |
| Ocean cells are land | COASTAL with divide | All boundary cells are land |
| Basin null when above sea | All cells above sea level | null |
| Basin found below sea | Cell with all samples below 63 | Boundary contains cell |
| Basin partial above | Some samples above, some below | Cell not in boundary |
| FLUVIAL has basin | FLUVIAL with below-sea-level cells | Basin boundary found |
| FLUVIAL no ocean | FLUVIAL region | Ocean detection returns null |

### Integration Points

After this implementation:
- Flowline Tracing can query `region.boundaries()` for terminus candidates
- Feature Classification can identify cells adjacent to boundaries
- Working set regions have complete boundary data

---

## Visualization Updates

### Update renderOceanBoundaries()

**File:** `client/RegionRenderer.java`

Rename `renderOceanBoundaries()` to `renderBoundaries()` and update to use new `Boundary` record:

```
renderBoundaries(region):
    for boundary in region.boundaries():
        color = boundary.type() == OCEAN ? BLUE : GREEN
        for cellPos in boundary.cells():
            drawBoundaryMarker(cellPos, color)
```

- Blue for ocean boundaries
- Green for basin boundaries

### Update ClientRegionCache

If `ClientRegionCache` stores boundary data, update to use `Boundary` instead of `OceanBoundary`.

---

## Migration Notes

### Breaking Changes

- `OceanBoundary` class deleted
- `Oceans.boundaries()` signature changed (SampleCache → CellCache, Set → Boundary)
- `Region.boundaries()` returns `List<Boundary>` not `Set<OceanBoundary>`

### Dependencies

Requires from Spatial Infrastructure:
- `Boundary` record
- `BoundaryType` enum
- `Cell.isOcean()`
- `Cell.isBasin()`
- `CellCache.getOrCompute(CellPos)`

All should be implemented as part of Spatial Infrastructure.
