---
level: 5
parent: "[[Boundary Identification]]"
status: pending
---

# Boundary Correction — Implementation

This document provides implementation guidance for correcting the existing boundary identification code to match the revised [[Boundary Identification]] specification. The previous implementation used an incorrect data structure and organization; this document describes how to fix it.

## Scope

This implementation covers:

- Redefining the `Boundary` record from cell-list to face representation
- Reorganizing from Oceans/Basins classes to Coastal/Fluvial classes
- Implementing shared helper methods for boundary checks
- Updating all consumers of the old structure

This does NOT cover:

- Flowline tracing to boundaries (see [[Flowline Tracing Implementation]])
- Other Spatial Infrastructure changes unrelated to Boundary

## The Problem

The previous implementation had two issues:

### Wrong Data Structure

| Aspect | Previous (Wrong) | Correct |
|--------|------------------|---------|
| Conceptual model | Boundary is a collection of cells | Boundary is a face between two cells |
| Data structure | `Boundary(List<CellPos> cells, BoundaryType type)` | `Boundary(CellPos land, CellPos water, BoundaryType type)` |
| Ocean return | Single `Boundary` with all coastline cells, or null | `List<Boundary>` with one entry per land-ocean face |
| Basin detection | Cells that ARE below sea level | Faces where land meets basin (the shore) |
| Direction info | Lost (must re-derive from D4 neighbors) | Explicit (land and water positions define direction) |

### Wrong Organization

| Aspect | Previous (Wrong) | Correct |
|--------|------------------|---------|
| Class structure | Oceans class, Basins class | Coastal class, Fluvial class |
| Loop count | Two loops for COASTAL regions | Single loop per region |
| Organization principle | By boundary type | By region type |

## Definition of Done

**What "done" means:**

- `Boundary` record has fields `land`, `water`, `type`
- `Boundary.direction()` returns D4 direction from land to water
- `Coastal.boundaries()` returns `List<Boundary>` (both ocean and basin)
- `Fluvial.boundaries()` returns `List<Boundary>` (basin only)
- `checkOceanBoundary()` and `checkBasinBoundary()` helpers exist
- Unit tests from spec pass
- `/rivertale boundaries` command shows individual faces
- `/rivertale vis boundaries` shows orange lines for ocean, red for basin

**What "done" does NOT mean:**

- Flowlines trace to these boundaries (that's Flowline Tracing)
- Rivers actually generate (downstream specs)

## Implementation Sequence

```
Phase 1: Boundary Record
    ├── Redefine Boundary(land, water, type)
    ├── Add direction() method
    └── Update encode()/decode() for network sync

Phase 2: Helper Methods
    ├── Create checkOceanBoundary()
    └── Create checkBasinBoundary()

Phase 3: Coastal Class
    ├── Create Coastal.boundaries()
    └── Single loop checking both ocean and basin

Phase 4: Fluvial Class
    ├── Create Fluvial.boundaries()
    └── Single loop checking basin only

Phase 5: Remove Old Classes
    ├── Delete Oceans class (or repurpose)
    └── Delete Basins class (or repurpose)

Phase 6: Consumer Updates
    ├── Update RiverShaping integration
    ├── Update visualization commands
    └── Update any code that used old classes

Phase 7: Tests
    └── Update/add unit tests per spec
```

---

## Phase 1: Boundary Record

### 1.1 Redefine Boundary

**File:** `core/Boundary.java` (or wherever currently defined)

**Current (wrong):**
```
record Boundary(cells: List<CellPos>, type: BoundaryType)
```

**Target:**
```
record Boundary(land: CellPos, water: CellPos, type: BoundaryType)

    direction(): FlowDirection
        dx = water.x - land.x
        dz = water.z - land.z

        if dx == 1 and dz == 0: return EAST
        if dx == -1 and dz == 0: return WEST
        if dx == 0 and dz == 1: return SOUTH
        if dx == 0 and dz == -1: return NORTH

        throw "Boundary cells must be D4 adjacent"
```

**Validation:** The constructor should validate that `land` and `water` are D4 adjacent (differ by exactly 1 in either x or z, not both).

### 1.2 Update Encoding

If `Boundary` has `encode()`/`decode()` methods for network sync:

**Current:**
```
encode(buf):
    buf.writeInt(cells.size())
    for cell in cells:
        buf.writeLong(cell.toLong())
    buf.writeEnum(type)
```

**Target:**
```
encode(buf):
    buf.writeLong(land.toLong())
    buf.writeLong(water.toLong())
    buf.writeEnum(type)

decode(buf): Boundary
    land = CellPos(buf.readLong())
    water = CellPos(buf.readLong())
    type = buf.readEnum(BoundaryType)
    return Boundary(land, water, type)
```

---

## Phase 2: Helper Methods

Create shared helper methods that both Coastal and Fluvial will use.

**File:** `terrain/BoundaryHelpers.java` (or as private methods in a shared location)

```
checkOceanBoundary(cell: CellPos, neighborPos: CellPos, neighbor: Cell, boundaries: List<Boundary>):
    if neighbor.isOcean():
        boundaries.add(Boundary(cell, neighborPos, OCEAN))

checkBasinBoundary(cell: CellPos, neighborPos: CellPos, neighbor: Cell, boundaries: List<Boundary>):
    if neighbor.isBasin():
        boundaries.add(Boundary(cell, neighborPos, BASIN))
```

These are simple helpers, but extracting them:
- Ensures consistent boundary creation
- Provides parallel structure for both boundary types
- Makes the main loops easier to read

---

## Phase 3: Coastal Class

### 3.1 Create Coastal.boundaries()

**File:** `terrain/Coastal.java` (new file, or rename from Oceans.java)

```
Coastal.boundaries(regionPos: RegionPos, cellCache: CellCache): List<Boundary>
    boundaries = new List<Boundary>()
    minCell = regionPos.getMinCell()
    cells = cellsPerRegion()

    for x in 0 to cells-1:
        for z in 0 to cells-1:
            cellPos = CellPos(minCell.x + x, minCell.z + z)
            cell = cellCache.getOrCompute(cellPos)

            if cell.isOcean() or cell.isBasin():
                continue

            for dir in D4:
                neighborPos = cellPos.relative(dir)
                neighbor = cellCache.getOrCompute(neighborPos)
                checkOceanBoundary(cellPos, neighborPos, neighbor, boundaries)
                checkBasinBoundary(cellPos, neighborPos, neighbor, boundaries)

    return boundaries
```

**Key points:**

- Single loop over all cells
- Skips ocean cells AND basin cells (neither can be the land side of a boundary)
- Checks both ocean and basin for each neighbor
- Returns empty list, never null

---

## Phase 4: Fluvial Class

### 4.1 Create Fluvial.boundaries()

**File:** `terrain/Fluvial.java` (new file, or rename from Basins.java)

```
Fluvial.boundaries(regionPos: RegionPos, cellCache: CellCache): List<Boundary>
    boundaries = new List<Boundary>()
    minCell = regionPos.getMinCell()
    cells = cellsPerRegion()

    for x in 0 to cells-1:
        for z in 0 to cells-1:
            cellPos = CellPos(minCell.x + x, minCell.z + z)
            cell = cellCache.getOrCompute(cellPos)

            if cell.isBasin():
                continue

            for dir in D4:
                neighborPos = cellPos.relative(dir)
                neighbor = cellCache.getOrCompute(neighborPos)
                checkBasinBoundary(cellPos, neighborPos, neighbor, boundaries)

    return boundaries
```

**Key points:**

- Single loop over all cells
- Only skips basin cells (no ocean in FLUVIAL regions)
- Only checks basin boundaries
- Returns empty list, never null

---

## Phase 5: Remove Old Classes

### 5.1 Delete or Repurpose Oceans

**File:** `terrain/Oceans.java`

If Oceans has other methods besides `boundaries()`, keep the class but remove the boundary detection. Otherwise, delete the file entirely.

### 5.2 Delete or Repurpose Basins

**File:** `terrain/Basins.java`

Same approach—remove boundary detection, keep other functionality if any.

---

## Phase 6: Consumer Updates

### 6.1 Update RiverShaping Integration

**File:** `world/RiverShaping.java` (or wherever boundary identification is called)

**Current pattern:**
```
if region.type == COASTAL:
    oceanBoundary = Oceans.boundaries(region.pos(), cellCache)
    if oceanBoundary != null:
        boundaries.add(oceanBoundary)
    basinBoundary = Basins.boundaries(region.pos(), cellCache)
    if basinBoundary != null:
        boundaries.add(basinBoundary)
else if region.type == FLUVIAL:
    basinBoundary = Basins.boundaries(region.pos(), cellCache)
    if basinBoundary != null:
        boundaries.add(basinBoundary)
```

**Target pattern:**
```
if region.type == COASTAL:
    boundaries = Coastal.boundaries(region.pos(), cellCache)
else if region.type == FLUVIAL:
    boundaries = Fluvial.boundaries(region.pos(), cellCache)
else:
    boundaries = []

region = region.withBoundaries(boundaries)
```

Much cleaner—one call per region type, no null checks.

### 6.2 Update Visualization

**File:** `client/RegionRenderer.java` or boundary visualization code

**Current (iterating cells):**
```
for boundary in region.boundaries():
    for cell in boundary.cells():
        drawMarker(cell, colorForType(boundary.type()))
```

**Target (drawing edge lines):**

Draw a line along the shared edge between the land and water cells:

- **OCEAN boundaries:** Orange line
- **BASIN boundaries:** Red line

```
for boundary in region.boundaries():
    color = ORANGE if boundary.type == OCEAN else RED

    landMin = boundary.land.getMinBlock()
    landMax = boundary.land.getMaxBlock()
    dir = boundary.direction()

    if dir == NORTH:
        drawLine(landMin.x, landMin.z, landMax.x, landMin.z, color)
    else if dir == SOUTH:
        drawLine(landMin.x, landMax.z + 1, landMax.x, landMax.z + 1, color)
    else if dir == WEST:
        drawLine(landMin.x, landMin.z, landMin.x, landMax.z, color)
    else if dir == EAST:
        drawLine(landMax.x + 1, landMin.z, landMax.x + 1, landMax.z, color)
```

The line runs along the full length of the cell edge where land meets water.

### 6.3 Update Commands

**File:** `command/BoundariesCommand.java` (or similar)

**Current output format:**
```
Boundaries for RegionPos(0, 0) - COASTAL:
  Ocean boundary: 47 cells
    Northwest corner: CellPos(0, 0)
    Southeast corner: CellPos(31, 12)
```

**Target output format:**
```
Boundaries for RegionPos(0, 0) - COASTAL:
  Ocean boundaries: 47 faces
  Basin boundaries: 3 faces

  Sample ocean boundaries:
    CellPos(5, 10) -> CellPos(5, 11) [SOUTH]
    CellPos(6, 10) -> CellPos(6, 11) [SOUTH]
    ...
```

### 6.4 Update Flowline Tracing Integration

Flowline Tracing will need to check if a cell is at a boundary. The check changes from:

**Current (wrong):**
```
// Check if cell is IN the boundary's cell list
for b in region.boundaries():
    if b.cells().contains(currentCell):
        return true  // At terminus
```

**Target:**
```
// Check if cell is the LAND side of any boundary
for b in region.boundaries():
    if b.land == currentCell:
        return true  // At terminus - can cross this face
```

Or more specifically, check if the flowline's next step would cross a boundary:
```
for b in region.boundaries():
    if b.land == currentCell and b.direction() == flowDirection:
        return true  // This step crosses into water
```

---

## Phase 7: Tests

### 7.1 Update Existing Tests

Any tests that:
- Created `Boundary` with a list of cells
- Called `Oceans.boundaries()` or `Basins.boundaries()`
- Iterated `boundary.cells()`

Need updating to the new structure.

### 7.2 Add New Tests

From the spec's validation criteria:

| Test | Setup | Expected |
|------|-------|----------|
| direction() NORTH | land=(5,5), water=(5,4) | NORTH |
| direction() SOUTH | land=(5,5), water=(5,6) | SOUTH |
| direction() EAST | land=(5,5), water=(6,5) | EAST |
| direction() WEST | land=(5,5), water=(4,5) | WEST |
| direction() throws for non-adjacent | land=(5,5), water=(7,5) | IllegalStateException |
| Coastal finds both types | COASTAL with ocean and depression | Both OCEAN and BASIN boundaries |
| Fluvial finds only basin | FLUVIAL with depression | Only BASIN boundaries |
| Multiple boundaries per cell | Land cell with ocean N and basin E | Two Boundary objects |
| Basin shore not lake | Basin depression | Boundaries at edge, not interior |

---

## Migration Notes

### Breaking Changes

- `Boundary` record signature changed completely
- `Oceans` class removed or repurposed
- `Basins` class removed or repurposed
- New `Coastal` and `Fluvial` classes
- Any code iterating `boundary.cells()` will not compile

### Network Protocol

If boundaries are synced over the network, the encoding format changes. Clients and servers must be updated together.

### Visualization

The visualization will look different—instead of highlighting cells, it shows colored lines along cell edges. Orange for ocean, red for basin.

---

## Validation Checklist

Before marking complete:

- [ ] `Boundary` record compiles with new signature
- [ ] `Boundary.direction()` works for all D4 directions
- [ ] `checkOceanBoundary()` helper exists
- [ ] `checkBasinBoundary()` helper exists
- [ ] `Coastal.boundaries()` returns both boundary types
- [ ] `Fluvial.boundaries()` returns basin boundaries only
- [ ] Old `Oceans`/`Basins` classes removed or repurposed
- [ ] RiverShaping uses new Coastal/Fluvial classes
- [ ] All compilation errors in consumers resolved
- [ ] Unit tests pass
- [ ] `/rivertale boundaries` shows face information
- [ ] `/rivertale vis boundaries` shows orange and red lines
