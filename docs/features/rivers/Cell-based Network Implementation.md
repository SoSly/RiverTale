# Cell-based Network Implementation Plan

This document outlines the build order for the cell-based networking system described in `Cell-based Networks.md`. The goal is to implement in testable increments, with debug visualization available early.

## Key Decisions

These decisions were made during planning and supersede any conflicting details in the design doc:

| Decision | Value | Rationale |
|----------|-------|-----------|
| Base cell size | 4096 blocks | Coarse enough for major drainage patterns, low performance cost |
| Subdivision factor | 7x | Odd number ensures edge centers land in a single subcell, not on a boundary |
| Minimum cell size | 128 blocks | Prevents subdivision from creating cells too small for meaningful flow |
| Number of passes | 2 | Main rivers + regional rivers. Simpler, faster generation |
| Density source | Abstracted | Design provider interface, implement Lithosphere adapter first |
| Cache persistence | SavedData | Consistent with existing ContinentCacheSavedData pattern |
| Debug visualization | Early | Add in Phase 2 for visual debugging throughout development |
| Density equality threshold | 0.01 | Prevents floating-point noise from causing unexpected flow |
| Base participation rate | 0.7 | Default 70% of cells participate, configurable |
| Participation decay | 0.25x per pass | Pass 2 has ~52.5% participation for sparser tributaries |

**Computed values:**

- Pass 1: 4096 blocks, 70% participation (major rivers)
- Pass 2: ~585 blocks (4096/7), 52.5% participation (regional tributaries)
- Pass 3 would be ~84 blocks (below 128 minimum), so system stops at 2 passes

Note: 4096/7 = 585.14 blocks per Pass 2 subcell. The non-integer size is acceptable—subcells are conceptual for flow decisions, not block placement boundaries. The carver interpolates smooth paths through subcell centers.

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                       DensityProvider                           │
│  - Interface: getDensity(x, z, samples) -> double               │
│  - LithosphereDensityProvider: depth * 0.7 + erosion * 0.3      │
│  - VanillaDensityProvider: continentalness (future)             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        RiverCell                                │
│  - Coordinates (cellX, cellZ, pass)                             │
│  - Cached density, participation, outputs, distance             │
│  - Computes flow by comparing density to neighbors              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     RiverCellManager                            │
│  - Owns the cell cache (per-level)                              │
│  - Handles cell lookup and lazy computation                     │
│  - Coordinates multi-pass hierarchy                             │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   RiverCellSavedData                            │
│  - Persists computed cells to world save                        │
│  - Extends SavedData like ContinentCacheSavedData               │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation Phases

### Phase 0: Validate Continentalness Assumption

**Goal:** Verify that `NoiseRouter.continents()` provides a usable density signal before building the rest of the system.

**Why this matters:**

The entire cell-based network relies on one assumption: density decreases as you approach the ocean. We're using `continents` from `NoiseRouter` as our density source. If this assumption is wrong—if coastal mountains have high continentalness, or inland basins have low continentalness—rivers will flow the wrong direction.

**Testing procedure:**

1. Create a test world with Lithosphere
2. Create a test world with vanilla Minecraft (no Lithosphere)
3. In each world, teleport to various locations and record the `continents` value:
   - Deep ocean (far from any land)
   - Coastline (standing at water's edge)
   - Inland plains (a few thousand blocks from coast)
   - Inland mountains (high elevation, far from coast)
   - Inland valleys (low elevation, far from coast)

**Expected results:**

| Location | Expected Continentalness |
|----------|-------------------------|
| Deep ocean | Below -0.13 (our ocean threshold) |
| Coastline | Near -0.13 |
| Inland plains | Positive, increasing with distance from coast |
| Inland mountains | Positive (elevation shouldn't matter, only distance from coast) |
| Inland valleys | Positive (elevation shouldn't matter, only distance from coast) |

**Red flags:**

- Coastal mountains with higher continentalness than inland plains → rivers would flow away from ocean
- Inland valleys with lower continentalness than coastal areas → rivers would terminate inland incorrectly
- Continentalness varying significantly with Y coordinate → 2D flow model breaks down

**If testing fails:**

If `continents` doesn't behave as expected, we need to either:
1. Find a different density function that does represent "distance from ocean"
2. Compute our own distance-from-ocean metric (more expensive, but guaranteed correct)
3. Accept that rivers may sometimes flow "wrong" in edge cases

**Validation:** Document findings. If continentalness works as expected, proceed to Phase 1. If not, revisit the density provider design before continuing.

---

### Phase 1: Data Structures

**Goal:** Define core types without computation logic.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverCell.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverCellKey.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/FlowDirection.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/DensityProvider.java`

**RiverCellKey:**

A record containing `cellX`, `cellZ`, and `pass`.

```
fromBlockPos(blockX, blockZ, pass):
    cellSize = getCellSize(pass)
    return RiverCellKey(
        floorDiv(blockX, cellSize),
        floorDiv(blockZ, cellSize),
        pass
    )

getCellSize(pass):
    size = 4096
    for i from 1 to pass-1:
        size = size / 8
    return max(size, 128)

worldX = cellX * getCellSize(pass)
worldZ = cellZ * getCellSize(pass)
centerX = worldX + getCellSize(pass) / 2
centerZ = worldZ + getCellSize(pass) / 2
```

**FlowDirection:**

An enum with values NORTH, SOUTH, EAST, WEST, and NONE. Each direction has a delta:

```
NORTH: dx=0, dz=-1
SOUTH: dx=0, dz=+1
EAST:  dx=+1, dz=0
WEST:  dx=-1, dz=0
NONE:  dx=0, dz=0

neighbor(from):
    if this == NONE:
        return null
    return RiverCellKey(from.cellX + dx, from.cellZ + dz, from.pass)
```

**RiverCell:**

A class containing:
- `key`: RiverCellKey
- `density`: double (averaged from subcellDensities, immutable)
- `subcellDensities`: double[7][7] (cached for path refinement, immutable)
- `classification`: CellClassification enum (LAND, OCEAN, or COASTAL)
- `participating`: boolean (immutable)
- `primaryOutput`: FlowDirection
- `secondaryOutputs`: Set of FlowDirection
- `distanceToOcean`: int (defaults to -1, meaning uncalculated)
- `isBasin`: boolean
- `riverPath`: List of subcell coordinates (set by Phase 8)

Plus getters and NBT serialization. The `subcellDensities` array is sampled once during cell creation and reused for both averaging and path refinement.

**CellClassification:**

An enum with three values:
- `LAND` — all subcells above ocean threshold; normal flow rules apply
- `OCEAN` — all subcells below ocean threshold; cell is a terminus with no outputs
- `COASTAL` — mixed subcells; cell participates but is a terminus (river ends at coastline)

Classification is determined during cell creation by checking each subcell density against the ocean threshold. If all are above: LAND. If all are below: OCEAN. If mixed: COASTAL.

**DensityProvider:**

An interface with three methods:
- `getDensity(worldX, worldZ)` → double
- `getAveragedDensity(worldX, worldZ, cellSize)` → double (always uses 7×7 grid)
- `isOcean(density)` → boolean

**Validation:** Code compiles, records serialize to NBT correctly.

---

### Phase 2: Debug Command

**Goal:** Provide cell data for debugging before implementing flow logic.

**Files:**
- `src/main/java/org/sosly/rivertale/command/CellCommand.java`

**Command:**

```
/rivertale cell
```

Prints information about the cells at the player's current position, one per pass:

```
Pass 1 - Cell (3, -7):
  Center: (14336, -26624)
  Density: 0.342
  Classification: LAND
  Participating: true
  Output: SOUTH
  Inputs: NORTH, WEST
  Distance to ocean: 12

Pass 2 - Cell (24, -56):
  Center: (12544, -28416)
  Density: 0.298
  Classification: LAND
  Participating: true
  Output: EAST
  Inputs: NORTH
  Distance to ocean: 9
```

If a cell is not participating, the output is simplified:

```
Pass 2 - Cell (24, -56):
  Participating: false
```

Ocean, coastal, and basin cells show their status:

```
Pass 1 - Cell (0, 12):
  Center: (2048, 51200)
  Density: -0.21
  Classification: OCEAN
```

```
Pass 1 - Cell (2, 8):
  Center: (10240, 34816)
  Density: 0.08
  Classification: COASTAL
  Participating: true
  Inputs: WEST
  Distance to ocean: 0
  River mouth at subcell: (3, 4)
```

```
Pass 1 - Cell (5, 3):
  Center: (22528, 14336)
  Density: 0.67
  Classification: LAND
  Basin: true
  Inputs: SOUTH, EAST, WEST
```

**Validation:** Command exists, outputs placeholder data (hardcoded values before density provider is implemented).

---

### Phase 3: Locate Command

**Goal:** Provide a way to find nearby river network features, similar to vanilla's `/locate`.

**Files:**
- Modify `src/main/java/org/sosly/rivertale/command/CellCommand.java` (or create separate `LocateCommand.java`)

**Command:**

```
/rivertale locate <feature>
```

**Available features (expanded as phases complete):**

| Feature | Available After | Description |
|---------|-----------------|-------------|
| `basin` | Phase 5 | Nearest cell that is a basin terminus |
| `ocean` | Phase 5 | Nearest cell classified as OCEAN |
| `coastal` | Phase 5 | Nearest cell classified as COASTAL (river mouth) |
| `source` | Phase 5 | Nearest cell with no inputs (river source) |
| `confluence` | Phase 5 | Nearest cell with multiple inputs |

**Future features (outside CBN scope):**

These would be added when river carving/decoration is implemented:

| Feature | Description |
|---------|-------------|
| `river` | Nearest river (actual carved feature) |
| `waterfall` | Nearest waterfall |
| `lake` | Nearest lake (distinct from basin—actual water feature) |
| `rapids` | Nearest rapids |

**Output format:**

Matches vanilla `/locate` format with clickable teleport coordinates:

```
/rivertale locate basin

The nearest basin is at [22528, 63, 14336] (3847 blocks away)
```

The coordinates are a clickable link that suggests `/tp @s 22528 63 14336`.

If none found within search radius:

```
Could not find a basin within 10000 blocks.
```

**Implementation notes:**

- Search outward from player position in a spiral or expanding grid
- Check cells at each pass level
- Stop at first match (or configurable: find N nearest)
- Search radius should be configurable, default ~10000 blocks
- For performance, can limit to cells already cached, or compute on-demand up to a limit

**Validation:** Command exists with stub responses. Real functionality added as each feature becomes detectable.

---

### Phase 4: Density Provider

**Goal:** Implement the abstracted density interface with support for both Lithosphere and vanilla Minecraft.

**⚠️ IMPLEMENTATION NOTE:** Before implementing this phase, do in-game testing to verify what the `continents` density function actually returns in both Lithosphere and vanilla worlds. The research in `.claude/lithosphere/` and `.claude/minecraft/` suggests `continents` is the right function for both, but this needs empirical validation. Test at various locations: deep ocean, coastline, inland, mountains, etc.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/DensityProvider.java` (interface)
- `src/main/java/org/sosly/rivertale/worldgen/river/ContinentsDensityProvider.java` (implementation)

**ContinentsDensityProvider:**

Both Lithosphere and vanilla expose a `continents` density function via `NoiseRouter.continents()`. This function determines how "continental" a location is—higher values are more inland, lower values are more oceanic. The same implementation should work for both:

```
Constants:
    OCEAN_THRESHOLD = -0.13  // Below this is ocean

getDensity(worldX, worldZ):
    return sampleContinents(worldX, worldZ)  // from NoiseRouter.continents()

getAveragedDensity(worldX, worldZ, cellSize):
    sum = 0
    step = cellSize / 7.0
    for row from 0 to 6:
        for col from 0 to 6:
            sum += getDensity(worldX + col * step, worldZ + row * step)
    return sum / 49

isOcean(density):
    return density < OCEAN_THRESHOLD
```

**Why `continents`:**

- Vanilla's `continents` is just shifted `minecraft:continentalness` noise
- Lithosphere's `continents` is more complex but serves the same purpose
- Both represent "how inland" a location is, which is exactly what we need for flow direction
- Lithosphere's `depth` and `erosion` are NOT suitable—`depth` is Y-dependent and `erosion` is about roughness, not distance from ocean

**Accessing the function:**

```
RandomState randomState = level.getChunkSource().randomState()
NoiseRouter router = randomState.router()
DensityFunction continentsFunction = router.continents()
```

**Sample grid resolution:**

All cells use a 7×7 subgrid (49 samples) regardless of cell size. This keeps the algorithm simple and ensures the same grid is available for both density averaging and path refinement.

| Cell Size | Step |
|-----------|------|
| 4096 | ~585 blocks |
| 512 | ~73 blocks |

The non-integer step sizes are fine—we're sampling continuous density functions, not aligning to block boundaries.

**Integration with Phase 2 command:**

Once the density provider is implemented, update `/rivertale cell` to display real density values instead of placeholder data. The command should now call `DensityProviderRegistry.getProvider(level)` and sample the cell's averaged density.

**Validation:**
1. Place player in world with Lithosphere
2. Run `/rivertale cell` at various locations
3. Verify density value is reasonable (not NaN, within expected range)
4. Verify ocean detection works near coastlines
5. Verify inland locations have higher density than coastal locations

---

### Phase 5: Flow Calculation

**Goal:** Implement the core flow algorithm.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverCellManager.java`

**Flow Algorithm:**

```
computeFlow(cell):
    // Classification was set during cell creation based on subcell densities
    if cell.classification == OCEAN:
        cell.primaryOutput = NONE
        return

    if cell.classification == COASTAL:
        // Coastal cells are terminuses—river ends at the coastline within this cell
        cell.primaryOutput = NONE
        // But they still receive inputs from higher-density land neighbors
        return

    // LAND cells: normal flow rules
    neighbors = getParticipatingNeighbors(cell)
    lowerNeighbors = neighbors where neighbor.density < cell.density - EPSILON

    if lowerNeighbors is empty:
        cell.isBasin = true
        cell.primaryOutput = NONE
        return

    // Sort by density ascending
    sorted = lowerNeighbors.sortBy(density)

    cell.primaryOutput = directionTo(sorted[0])
    cell.secondaryOutputs = sorted[1..].map(directionTo)
```

**Coastal cell handling:**

Coastal cells participate in the river network (they contain land) but are terminuses. They have no primaryOutput because the river ends at the coastline within the cell, not at an edge center. They can still receive inputs from higher-density land neighbors—water flows into them and terminates at the ocean subcells.

During path refinement (Phase 8), the path in a coastal cell terminates when it reaches an ocean subcell rather than continuing to an edge center.

**Understanding secondary outputs:**

Secondary outputs are NOT water splitting. They are NEW RIVER SOURCES.

If a cell has density 0.5 and two neighbors are lower (North at 0.3, South at 0.4), the cell outputs primarily to North (main flow) and secondarily to South (new source). Water from upstream flows through this cell to North. But this cell is ALSO a local high point relative to South, so it's the SOURCE of a separate river flowing South. The cell sits on a drainage divide.

See `Cell-based Networks.md` for a detailed diagram and explanation.

**Tiebreaker:**

When two neighbors have equal density (within EPSILON = 0.01):

```
breakTie(cell, tied):
    seed = cell.cellX * 31 + cell.cellZ * 17 + cell.pass * 7
    rng = seededRandom(seed XOR worldSeed)
    return tied[rng.nextInt(tied.length)]
```

The tiebreaker is deterministic from coordinates and world seed.

**Participation Check:**

```
isParticipating(key):
    baseRate = 0.7
    decayPerPass = 0.25
    rate = baseRate * (1 - decayPerPass)^(key.pass - 1)

    seed = key.cellX * 31 + key.cellZ * 17 + key.pass * 7
    rng = seededRandom(seed XOR worldSeed)
    return rng.nextDouble() < rate
```

**Integration with Phase 2 command:**

Update `/rivertale cell` to show classification, output direction, and inputs. These are now computed on-the-fly by calling the flow algorithm—no caching yet.

**Validation:**
1. Generate cells in a test area
2. Run `/rivertale cell` at various locations
3. Verify output direction points toward lower density
4. Verify cells near coastlines output toward ocean
5. Verify no cell outputs toward a higher-density neighbor
6. Verify basins appear in local minima (no lower neighbor)

---

### Phase 6: Distance to Ocean

**Goal:** Implement lazy recursive distance calculation.

**Files:**
- Modify `RiverCellManager.java`
- Modify `RiverCell.java`

**Algorithm:**

```
getDistanceToOcean(cell):
    if cell.distanceToOcean >= 0:
        return cell.distanceToOcean  // cached

    // All terminuses have distance 0
    if cell.classification == OCEAN or cell.classification == COASTAL:
        cell.distanceToOcean = 0
        return 0

    if cell.isBasin:
        cell.distanceToOcean = 0
        return 0

    downstream = getCell(cell.primaryOutput.neighbor(cell.key))
    cell.distanceToOcean = 1 + getDistanceToOcean(downstream)
    return cell.distanceToOcean
```

**Recursion Safety:**

The acyclic guarantee from the design doc ensures recursion terminates. Flow always moves toward lower density, and density comparisons are transitive. However, we add a safety limit:

```
MAX_RECURSION_DEPTH = 1000

getDistanceToOcean(cell, depth):
    if depth > MAX_RECURSION_DEPTH:
        log warning "Distance calculation exceeded max depth at {cell.key}"
        return depth
    // ... rest of algorithm
```

This catches bugs during development without infinite loops.

**Integration with Phase 2 command:**

Update `/rivertale cell` to include "Distance to ocean" in the output. This value comes from `getDistanceToOcean()` and represents how many cells away from a terminus (ocean or basin) this cell is.

**Validation:**
1. Run `/rivertale cell` on a coastal cell
2. Verify distance is 0 (coastal cells are terminuses)
3. Run on a land cell adjacent to coastal
4. Verify distance is 1
5. Run on an inland cell further from coast
6. Verify distance increases with distance from coast
7. Verify basin cells have distance 0

---

### Phase 7: Cache Persistence

**Goal:** Save computed cells to world data.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverCellSavedData.java`
- Modify `RiverCellManager.java`

**RiverCellSavedData:**

Follows the pattern established by `ContinentCacheSavedData`. Extends SavedData with:
- DATA_NAME = "rivertale_cells"
- Thread-safe map of RiverCellKey → RiverCell

```
get(level):
    return level.dataStorage.computeIfAbsent(load, new, DATA_NAME)

save(tag):
    cellList = new list
    for each cell in cells:
        cellList.add(cell.save())
    tag.put("cells", cellList)
    return tag

load(tag):
    data = new RiverCellSavedData()
    cellList = tag.getList("cells")
    for each entry in cellList:
        cell = RiverCell.load(entry)
        data.cells.put(cell.key, cell)
    return data
```

**Cache Integration with Thread Safety:**

Multiple chunk generation threads may query cells simultaneously. `RiverCellManager` must handle concurrent access safely:

```
RiverCellManager:
    computing = concurrent map of RiverCellKey → Future<RiverCell>

    getCell(key):
        // Fast path: already cached
        cached = savedData.get(key)
        if cached != null:
            return cached

        // Slow path: compute with deduplication
        future = computing.computeIfAbsent(key, k => async computeCell(k))

        try:
            result = future.get()
            savedData.put(key, result)
            savedData.setDirty()
            computing.remove(key)
            return result
        catch exception:
            computing.remove(key)
            throw "Cell computation failed for {key}"
```

When two threads query the same uncached cell:
1. First thread creates a future and starts computation
2. Second thread gets the same future and blocks waiting
3. When computation completes, both threads receive the result
4. Only one computation happens per cell

`ConcurrentHashMap` handles the cell map. `setDirty()` is safe since SavedData's dirty flag is a simple boolean.

**Integration with Phase 2 command:**

Update `/rivertale cell` to check the cache first before computing on-the-fly. If the cell is cached, display the cached values. If not, compute on-the-fly as before. This lets the command work both during development (before worldgen integration) and in production (reading cached data).

Optionally add a `[cached]` or `[computed]` indicator to show where the data came from during debugging.

**Validation:**
1. Generate some cells, note their values
2. Save and quit
3. Reload world
4. Verify cells have same values without recomputation (check logs for computation messages)
5. Generate chunks in a large area rapidly (teleport around)
6. Verify no duplicate computation messages for the same cell

---

### Phase 8: River Path Refinement

**Goal:** Determine the actual path each river takes within its cell.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverPathRefiner.java`
- Modify `RiverCell.java` to store path data

**Why this is needed:**

A cell's flow direction (e.g., "outputs EAST") tells you WHERE water exits, but not the PATH it takes to get there. A cell with west input and east output could have a straight river, an S-curve, or a meandering path. Without knowing the path, the carving system doesn't know where to place blocks.

**Algorithm:**

The refinement algorithm uses the same 7×7 subcell grid that Pass 2 will use for tributaries. Why 7×7? An odd subdivision guarantees a true center subcell on each edge—entry and exit points land in subcell 3 (indices 0-6), not on a boundary between cells.

For each participating Pass 1 cell with inputs and outputs:

1. Identify entry subcell(s) — which subcell(s) contain the input edge center(s)
2. Identify exit subcell — which subcell contains the output edge center
3. Sample density at each subcell
4. Apply exit-direction weighting to create a virtual gradient toward the exit
5. Pathfind using weighted costs

**Why weighting is necessary:**

Raw terrain density doesn't conveniently slope toward the cell's exit. A cell might have its lowest density in the southwest corner while the exit is to the east. Without weighting, the river would flow southwest and never reach the exit.

The weighting adds a penalty based on distance from the exit:

```
weighted_cost = raw_density + (manhattan_distance_to_exit * weight_factor)
```

A weight factor of ~0.05 is enough to ensure eastward progress while still allowing density-driven meandering. See `Cell-based Networks.md` for a worked example with actual numbers.

```
refineRiverPath(cell):
    if not cell.participating or cell.classification == OCEAN or cell.isBasin:
        return  // No path needed

    entry = getSubcellAtEdge(cell, cell.inputDirections[0])  // e.g., (3,0) for west

    // Get the 7x7 density grid (already sampled during cell density averaging)
    rawDensities = cell.subcellDensities  // cached from getAveragedDensity()

    if cell.classification == COASTAL:
        // Coastal cells: path terminates at first ocean subcell
        cell.riverPath = findPathToOcean(entry, rawDensities)
    else:
        // Land cells: path goes to exit edge center
        exit = getSubcellAtEdge(cell, cell.primaryOutput)  // e.g., (3,6) for east
        costs = applyExitWeighting(rawDensities, exit)
        cell.riverPath = findPath(entry, exit, costs, rawDensities)

applyExitWeighting(rawDensities, exit):
    costs = empty 7x7 grid
    for each subcell (row, col):
        distance = manhattanDistance((row, col), exit)
        costs[row][col] = rawDensities[row][col] + (distance * 0.05)
    return costs

findPath(entry, exit, costs, rawDensities):
    path = [entry]
    visited = {entry}
    current = entry

    while current != exit:
        // Early termination: if exit is adjacent, go there
        if exit in getAdjacent(current):
            path.add(exit)
            return path

        neighbors = getAdjacentUnvisited(current, visited)

        // Try to find a lower-cost neighbor
        lowerCost = neighbors.filter(n => costs[n] < costs[current])
        if lowerCost is not empty:
            next = lowerCost.minBy(n => costs[n])
        else:
            // Stuck at local minimum: find unvisited neighbor closer to exit
            currentDist = manhattanDistance(current, exit)
            closer = neighbors.filter(n => manhattanDistance(n, exit) < currentDist)
            if closer is not empty:
                next = closer.minBy(n => costs[n])
            else:
                // Dead end: all unvisited neighbors are farther from exit
                // Backtrack and try alternate routes
                path.pop()
                if path is empty:
                    error "No path exists"  // Should never happen
                current = path.last()
                continue

        path.add(next)
        visited.add(next)
        current = next

    return path

findPathToOcean(entry, rawDensities):
    // For coastal cells: follow density gradient until reaching ocean subcell
    path = [entry]
    visited = {entry}
    current = entry

    while true:
        // Check if current subcell is ocean
        if rawDensities[current] < OCEAN_THRESHOLD:
            return path  // River has reached the sea

        neighbors = getAdjacentUnvisited(current, visited)

        if neighbors is empty:
            // Shouldn't happen in a coastal cell, but handle gracefully
            return path

        // Move toward lowest density (toward ocean)
        next = neighbors.minBy(n => rawDensities[n])
        path.add(next)
        visited.add(next)
        current = next

    return path
```

**Coastal path refinement:**

Coastal cells don't have an exit edge center—the river ends at the coastline within the cell. The `findPathToOcean` algorithm follows the raw density gradient (no exit weighting needed) until it reaches a subcell below the ocean threshold. That subcell is where the river meets the sea.

**Path representation:**

The path is stored as a list of subcell coordinates. The carving system interpolates between these to place actual river blocks.

**Multiple inputs (confluence):**

If a cell has multiple inputs, run pathfinding from each input independently. Track visited cells across all paths. When a path enters a subcell already visited by another path, the paths merge at that point—the later path joins the earlier path's route from there to the exit.

If paths don't intersect until the exit itself, that's fine. Two streams entering from opposite sides and both reaching the exit edge is a valid confluence—they merge at the output.

**Output:**

Each participating cell now has:
- `riverPath` — ordered list of subcells the river passes through
- Entry point(s) and exit point in subcell coordinates

**Validation:**
1. Run `/rivertale cell` on a participating cell
2. Verify path data is present
3. Verify path connects input edge to output edge
4. Verify path prefers lower-density subcells (visual inspection of debug output)

---

### Phase 9: Multi-Pass Hierarchy

**Goal:** Implement Pass 2 tributaries that feed into the Pass 1 river network.

**Files:**
- Modify `RiverCellManager.java`
- Modify `RiverCell.java`

**Critical concept: Pass 2 does NOT subdivide or recalculate Pass 1 rivers.**

Pass 1 establishes the main river network. The refinement step (Phase 8) determines where Pass 1 rivers actually go within each cell. Pass 2 adds tributaries—smaller streams that feed INTO those refined Pass 1 river paths. Pass 2 cells use density comparison independently; they don't inherit constraints from Pass 1 or affect Pass 1 in any way.

Think of it as:
- Pass 1 = highways
- Pass 2 = side roads feeding into highways

**Cells nest like matryoshka dolls:**

Every block position exists inside exactly one cell per pass. With a subdivision factor of 7, each Pass 1 cell contains 49 Pass 2 cells (7×7). Standing anywhere in the world, `/rivertale cell` returns one cell per pass—and each smaller cell is entirely inside the larger one.

```
One Pass 1 cell contains 7×7 = 49 Pass 2 cells:

     0   1   2   3   4   5   6
   ┌───┬───┬───┬───┬───┬───┬───┐
 0 │   │   │   │   │   │   │   │
   ├───┼───┼───┼───┼───┼───┼───┤
 1 │   │   │   │   │   │   │   │
   ├───┼───┼───┼───┼───┼───┼───┤
 2 │   │   │ X │   │   │   │   │  ← You are in Pass 2 cell (2,2)
   ├───┼───┼───┼───┼───┼───┼───┤     within this Pass 1 cell
 3 │   │   │   │   │   │   │   │  ← center row (edge centers here)
   ├───┼───┼───┼───┼───┼───┼───┤
 4 │   │   │   │   │   │   │   │
   ├───┼───┼───┼───┼───┼───┼───┤
 5 │   │   │   │   │   │   │   │
   ├───┼───┼───┼───┼───┼───┼───┤
 6 │   │   │   │   │   │   │   │
   └───┴───┴───┴───┴───┴───┴───┘
```

**Pass 2 cells are bounded by their parent Pass 1 cell.**

A Pass 2 cell only considers neighbors within the same Pass 1 cell. Tributaries cannot cross Pass 1 cell boundaries. This keeps the Pass 1 network authoritative—all water within a Pass 1 cell either drains to that cell's river or collects in a local pond.

**Pass 2 flow calculation:**

Pass 2 cells calculate flow by comparing density to participating neighbors within the same parent cell. The algorithm is similar to Pass 1, but neighbors outside the parent cell are excluded.

```
computePass2Flow(cell):
    parentKey = getParentPass1Cell(cell.key)

    // Only consider neighbors within the same Pass 1 cell
    neighbors = getParticipatingNeighbors(cell)
        .filter(n => getParentPass1Cell(n.key) == parentKey)

    lowerNeighbors = neighbors where neighbor.density < cell.density - EPSILON

    if lowerNeighbors is empty:
        cell.isBasin = true  // Local pond within this Pass 1 cell
        cell.primaryOutput = NONE
        return

    sorted = lowerNeighbors.sortBy(density)
    cell.primaryOutput = directionTo(sorted[0])
    cell.secondaryOutputs = sorted[1..].map(directionTo)
```

**How tributaries connect to the main river:**

Pass 2 tributaries flow toward lower density until they either:
1. Reach a subcell that contains the Pass 1 river path (merge point)
2. Reach a local minimum within the parent cell (becoming a small pond)

The Pass 1 river path (from Phase 8) runs through specific subcells. When a Pass 2 tributary's flow direction leads it into one of those subcells, it has reached the main river. The carving system places the visual merge at that point.

**Validation:**
1. Generate Pass 1 cells for an area
2. Generate Pass 2 cells within a Pass 1 cell
3. Verify Pass 2 cells flow toward lower density (not toward Pass 1 entry/exit)
4. Verify Pass 2 cells that reach local minima become basins (ponds)
5. Verify `/rivertale cell` shows both passes with independent flow directions

---

### Phase 10: Upstream Accumulation

**Goal:** Implement limited-depth upstream counting for width calculation.

**Files:**
- Modify `RiverCell.java`
- Modify `RiverCellManager.java`

**Algorithm:**

```
getUpstreamCount(key):
    return countUpstream(key, 0, emptySet)

countUpstream(key, depth, visited):
    if depth > config.upstreamDepthLimit:
        return 0
    if key in visited:
        return 0
    visited.add(key)

    count = 0
    for each dir in [NORTH, SOUTH, EAST, WEST]:
        neighborKey = dir.neighbor(key)
        neighbor = getCell(neighborKey)

        if not neighbor.isParticipating:
            continue

        // Does this neighbor flow into us?
        neighborOutput = neighbor.primaryOutput
        if neighborOutput != null and neighborOutput.neighbor(neighborKey) == key:
            count += 1 + countUpstream(neighborKey, depth + 1, visited)
    return count
```

**Note:** Upstream count is computed on demand, not cached. The depth limit (`upstreamDepthLimit` in config, default 3) keeps computation cheap. The count only needs to distinguish "few" from "many" for width calculation—higher limits provide more granularity at the cost of more cell queries.

**Width Derivation:**

The river feature generator will use upstream count to determine width:

| Upstream Count | Width Category |
|----------------|----------------|
| 0 | Source stream |
| 1-2 | Minor tributary |
| 3-5 | Regional river |
| 6+ | Major river |

Actual block widths are the feature generator's concern, not the cell system's.

**Validation:**
1. Find a cell with multiple upstream feeders
2. Run `/rivertale cell` and verify upstream count
3. Verify count is limited by depth (doesn't propagate infinitely)
4. Verify a source cell (no inputs) has upstream count 0

---

### Phase 11: Worldgen Integration

**Goal:** Trigger cell computation automatically during chunk generation.

**Files:**
- `src/main/java/org/sosly/rivertale/mixin/ServerChunkCacheMixin.java` (or event handler)

**Hook point:**

During chunk generation, compute cells for any cell that overlaps the chunk being generated. This ensures cell data exists in the cache before the river carving system (a separate system outside this document) needs it.

```
onChunkGenerate(chunk):
    chunkWorldX = chunk.getPos().getMinBlockX()
    chunkWorldZ = chunk.getPos().getMinBlockZ()

    for each pass:
        cellSize = getCellSize(pass)
        cellX = floor(chunkWorldX / cellSize)
        cellZ = floor(chunkWorldZ / cellSize)

        // getCell triggers computation if not cached
        cell = riverCellManager.getCell(new RiverCellKey(cellX, cellZ, pass))
```

**When to trigger:**

Hook into chunk generation after biome placement but before feature generation. The cell data needs to exist before any river carving features run.

For Forge 1.20.1, this could be:
- A mixin to `ServerChunkCache` or `ChunkGenerator`
- The `ChunkEvent.Load` event (though this fires after generation completes)

The exact hook point depends on when river carving will run, which is outside this document's scope.

**What this phase does NOT do:**

- Carve terrain
- Place water blocks
- Modify biomes
- Generate any visible features

This phase only ensures cell network data is computed and cached. A separate river carving system consumes this data to actually shape the terrain.

**Validation:**
1. Create a new world
2. Walk around to generate chunks
3. Run `/rivertale cell` — verify data shows `[cached]` not `[computed]`
4. Check logs for cell computation messages during chunk generation
5. Verify no duplicate computations for the same cell

---

### Phase 12: Configuration

**Goal:** Expose tuning parameters for users.

**Files:**
- `src/main/java/org/sosly/rivertale/config/RiverConfig.java`

**Configurable Values:**

```
baseCellSize = 4096
subdivisionFactor = 7              // odd number ensures edge centers land in single subcell
minimumCellSize = 128
maxPasses = 2
baseParticipationRate = 0.7        // range: 0.0 to 1.0
participationDecay = 0.25          // range: 0.0 to 1.0
densityEqualityThreshold = 0.01
oceanThreshold = -0.13
upstreamDepthLimit = 3             // how many cells upstream to count for width
```

**Note:** Configuration changes only affect newly generated cells. Existing cached cells retain their original values. This is intentional to maintain coherence within a world.

**Validation:**
1. Modify config values
2. Generate new world
3. Verify cells reflect new configuration
4. Verify existing worlds are unaffected

---

## Testing Milestones

| After Phase | Testable Behavior |
|-------------|-------------------|
| 2 | `/rivertale cell` command exists, outputs placeholder data for all passes |
| 3 | `/rivertale locate` command exists with stub responses |
| 4 | Density values appear correctly in cell info |
| 5 | Output direction points toward lower density neighbor; locate commands work |
| 6 | Distance to ocean increases inland |
| 7 | Cells persist across save/load; no race conditions during parallel generation |
| 8 | River paths determined within cells; paths connect input to output edges |
| 9 | Pass 2 cells flow independently; tributaries bounded by parent cell |
| 10 | Upstream count reflects feeder topology |
| 11 | Cells computed automatically during chunk generation; `/rivertale cell` shows `[cached]` |
| 12 | Config changes affect new worlds |

---

## Consumer Interface

Once the cell-based network is complete, it provides the following data to downstream systems (river carving, decoration):

**Per-Cell Data:**
- `RiverCellKey` - coordinates and pass number
- `density` - averaged terrain density
- `classification` - LAND, OCEAN, or COASTAL
- `primaryOutput` - direction water exits (N/S/E/W or NONE)
- `secondaryOutputs` - additional exit directions for tributaries
- `distanceToOcean` - cell count to nearest terminus (0 for OCEAN, COASTAL, and basins)
- `upstreamCount` - feeders within limited depth
- `isBasin` - true if this cell is a basin terminus (endorheic, no outlet)
- `riverPath` - list of subcell coordinates the river passes through (for LAND and COASTAL cells)

**Edge Centers:**

Rivers cross cell boundaries at edge centers only. World coordinates for edge centers:

```
getEdgeCenter(key, edge):
    cellSize = getCellSize(key.pass)
    baseX = key.worldX
    baseZ = key.worldZ

    NORTH: (baseX + cellSize/2, 0, baseZ)
    SOUTH: (baseX + cellSize/2, 0, baseZ + cellSize)
    EAST:  (baseX + cellSize, 0, baseZ + cellSize/2)
    WEST:  (baseX, 0, baseZ + cellSize/2)
    NONE:  null
```

Adjacent cells always agree on connection points because edge centers are defined by world coordinates, not cell-relative positions.


