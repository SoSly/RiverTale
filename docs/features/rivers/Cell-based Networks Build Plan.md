---
level: 5
parent: "[[Cell-based Networks Architecture]]"
status: review
---

# Cell-based Network Implementation Plan

This document outlines the build order for the cell-based networking system described in `Cell-based Networks Architecture.md`. The goal is to implement in testable increments, with debug visualization available early.

## Key Decisions

These decisions were made during planning and supersede any conflicting details in the design doc:

| Decision | Value | Rationale |
|----------|-------|-----------|
| Cell size | 256 blocks | Fine enough for detailed rivers, performance is not a concern (5-20ms even at 128 blocks) |
| Density source | Abstracted | Design provider interface, implement Lithosphere adapter first |
| Density formula | continents + (depth × 1.0) | Continents for ocean direction, depth weighted 1.0 for aggressive terrain-following |
| Cache persistence | SavedData | Consistent with existing ContinentCacheSavedData pattern |
| Debug visualization | Early | Add in Phase 2 for visual debugging throughout development |
| Density equality threshold | 0.01 | Prevents floating-point noise from causing unexpected flow |
| Participation rate | 0.7 | Default 70% of cells participate, configurable |
| Lake threshold | depth < 0 | Terrain at or below sea level (y=63) is lake; creates LAKE/LAKESHORE classifications |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                       DensityProvider                           │
│  - Interface: getDensity(x, z) -> double                        │
│  - ContinentsDensityProvider: continents + (depth * 1.0)        │
│    Works for both vanilla and Lithosphere                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                        RiverCell                                │
│  - Coordinates (cellX, cellZ)                                   │
│  - Cached density, participation, outputs, distance             │
│  - Computes flow by comparing density to neighbors              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     RiverCellManager                            │
│  - Owns the cell cache (per-level)                              │
│  - Handles cell lookup and lazy computation                     │
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

### Phase 1: Data Structures

**Goal:** Define core types without computation logic.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverCell.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverCellKey.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/FlowDirection.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/DensityProvider.java`

**RiverCellKey:**

A record containing `cellX` and `cellZ`.

```
CELL_SIZE = 256  // constant

fromBlockPos(blockX, blockZ):
    return RiverCellKey(
        floorDiv(blockX, CELL_SIZE),
        floorDiv(blockZ, CELL_SIZE)
    )

worldX = cellX * CELL_SIZE
worldZ = cellZ * CELL_SIZE
centerX = worldX + CELL_SIZE / 2
centerZ = worldZ + CELL_SIZE / 2
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
    return RiverCellKey(from.cellX + dx, from.cellZ + dz)
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

An enum with five values representing two parallel terminus hierarchies:

**Ocean terminuses (continents threshold):**
- `OCEAN` — all subcells below ocean threshold (continents < -0.13); terminus with no outputs
- `COASTAL` — mixed ocean/land subcells; participates but terminus (river ends at coastline)

**Lake terminuses (depth threshold):**
- `LAKE` — all subcells have depth < 0 (terrain at/below y=63), not ocean; terminus with no outputs
- `LAKESHORE` — mixed positive/negative depth subcells; participates but terminus (river drains into lake)

**Normal flow:**
- `LAND` — all subcells have depth ≥ 0 and not ocean; normal flow rules apply

Additionally, LAND cells can become **basins** if no participating neighbor has lower density. Basins are flow-based local minima where new lakes form, distinct from terrain-based LAKE cells.

Classification is determined during cell creation by checking each subcell against both thresholds. Ocean threshold is checked first (takes precedence), then lake threshold.

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

Prints information about the cell at the player's current position:

```
Cell (3, -7):
  Center: (1792, -3328)
  Density: 0.342
  Classification: LAND
  Participating: true
  Output: SOUTH
  Inputs: NORTH, WEST
  Distance to ocean: 12
```

If a cell is not participating, the output is simplified:

```
Cell (24, -56):
  Participating: false
```

Ocean, coastal, and basin cells show their status:

```
Cell (0, 12):
  Center: (256, 6400)
  Density: -0.21
  Classification: OCEAN
```

```
Cell (2, 8):
  Center: (1280, 4352)
  Density: 0.08
  Classification: COASTAL
  Participating: true
  Inputs: WEST
  Distance to ocean: 0
  River mouth at subcell: (3, 4)
```

```
Cell (5, 3):
  Center: (2816, 1792)
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
| `coastal` | Phase 5 | Nearest cell classified as COASTAL (river mouth to ocean) |
| `lake` | Phase 5 | Nearest cell classified as LAKE (terrain-based freshwater) |
| `lakeshore` | Phase 5 | Nearest cell classified as LAKESHORE (river drains into lake) |
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
- Stop at first match (or configurable: find N nearest)
- Search radius should be configurable, default ~10000 blocks
- For performance, can limit to cells already cached, or compute on-demand up to a limit

**Validation:** Command exists with stub responses. Real functionality added as each feature becomes detectable.

---

### Phase 4: Density Provider

**Goal:** Implement the abstracted density interface with support for both Lithosphere and vanilla Minecraft.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/DensityProvider.java` (interface)
- `src/main/java/org/sosly/rivertale/worldgen/river/ContinentsDensityProvider.java` (implementation)

**Empirical Testing Results (Vanilla Minecraft):**

Testing was performed at various locations using `/rivertale c` to sample noise values:

| Location | Terrain Height | Continents | Depth (y=63) | our density |
|----------|---------------|------------|--------------|-------------|
| Deep Ocean | 63 | -0.661 | -0.466 | 0.060       |
| Ocean | 63 | -0.302 | -0.153 | 0.060       |
| Beach | 63 | -0.127 | -0.046 | 0.060       |
| Plains | 93 | 0.246 | 0.159 | 0.060       |
| Grove (mountain) | 201 | 0.213 | 0.913 | -0.687      |
| Frozen Peaks | 255 | 0.306 | 1.288 | -0.687      |

**Key findings:**

1. **Continents does NOT vary with Y.** Values at y=63 and y=terrain_height are identical. This is essential for a 2D flow model.

2. **Depth IS Y-dependent, but useful at y=63.** At terrain height, depth hovers near 0. At y=63, it correlates with terrain elevation (higher terrain = higher depth value).

3. **Erosion doesn't correlate with distance from ocean.** It reflects terrain roughness: 0.060 for smooth areas, -0.687 for mountains.

4. **The -0.13 ocean threshold is correct.** Beach lands at -0.127, right at the threshold.

5. **Continents alone has insufficient local gradient.** Adjacent locations (mountain slope at height 159 vs valley at height 92) showed continents values of 0.283 vs 0.291—the valley was *higher*, which would cause water to flow uphill. The delta (0.008) is within our 0.01 epsilon.

**Density Formula:**

To ensure rivers follow terrain while draining toward ocean, combine continents with depth:

```
density = continents + (depth_at_y63 * DEPTH_WEIGHT)
```

Where `DEPTH_WEIGHT = 1.0` (configurable). This ensures:
- Continents provides macro direction (toward ocean)
- Depth aggressively follows terrain (rivers flow into valleys and lakes)

**ContinentsDensityProvider:**

```
Constants:
    OCEAN_THRESHOLD = -0.13  // Below this is ocean
    LAKE_THRESHOLD = 0.0     // Depth below this is lake (terrain at/below y=63)
    DEPTH_WEIGHT = 1.0       // How much terrain elevation influences flow
    SAMPLE_Y = 63            // Fixed Y for consistent 2D sampling

getDensity(worldX, worldZ):
    continents = sampleContinents(worldX, SAMPLE_Y, worldZ)
    depth = sampleDepth(worldX, SAMPLE_Y, worldZ)
    return continents + (depth * DEPTH_WEIGHT)

getAveragedDensity(worldX, worldZ, cellSize):
    sum = 0
    step = cellSize / 7.0
    for row from 0 to 6:
        for col from 0 to 6:
            sum += getDensity(worldX + col * step, worldZ + row * step)
    return sum / 49

isOcean(density):
    // Use raw continents for ocean detection, not combined density
    return sampleContinents(worldX, SAMPLE_Y, worldZ) < OCEAN_THRESHOLD
```

**Why this formula:**

- **Continents** provides macro direction toward ocean
- **Depth at y=63** provides terrain awareness without Y-dependency
- **Weight of 1.0** ensures rivers aggressively follow terrain, flowing into valleys and lakes
- Example: Mountain (continents 0.320, depth 1.738) → combined 2.058; Lake area (continents 0.235, depth -0.070) → combined 0.165. Water correctly flows from high terrain to low terrain.

**Accessing the functions:**

```
RandomState randomState = level.getChunkSource().randomState()
NoiseRouter router = randomState.router()
DensityFunction continentsFunction = router.continents()
DensityFunction depthFunction = router.depth()
```

**Sample grid resolution:**

All cells use a 7×7 subgrid (49 samples) regardless of cell size. This keeps the algorithm simple and ensures the same grid is available for both density averaging and path refinement.

With 256-block cells, each subcell step is ~37 blocks. The non-integer step size is fine—we're sampling continuous density functions, not aligning to block boundaries.

**Integration with Phase 2 command:**

Once the density provider is implemented, update `/rivertale cell` to display real density values instead of placeholder data. The command should now call `DensityProviderRegistry.getProvider(level)` and sample the cell's averaged density.

**Validation:**
1. Place player in world
2. Run `/rivertale cell` at various locations
3. Verify density value is reasonable (not NaN, within expected range)
4. Verify ocean detection works near coastlines
5. Verify inland locations have higher density than coastal locations
6. Verify mountain locations have higher density than adjacent valleys

---

### Phase 5: Flow Calculation

**Goal:** Implement the core flow algorithm.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverCellManager.java`

**Flow Algorithm:**

```
computeFlow(cell):
    // Classification was set during cell creation based on subcell densities
    // All terminus types have no output
    if cell.classification in [OCEAN, LAKE]:
        cell.primaryOutput = NONE
        return

    if cell.classification in [COASTAL, LAKESHORE]:
        // Coastal/lakeshore cells are terminuses—river ends within this cell
        cell.primaryOutput = NONE
        // But they still receive inputs from higher-density neighbors
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
    seed = cell.cellX * 31 + cell.cellZ * 17
    rng = seededRandom(seed XOR worldSeed)
    return tied[rng.nextInt(tied.length)]
```

The tiebreaker is deterministic from coordinates and world seed.

**Participation Check:**

```
isParticipating(key):
    rate = config.participationRate  // default 0.7

    seed = key.cellX * 31 + key.cellZ * 17
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
    if cell.classification in [OCEAN, COASTAL, LAKE, LAKESHORE]:
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

Multiple chunk generation threads may query cells simultaneously. Use `ConcurrentHashMap.computeIfAbsent` for thread-safe deduplication:

```
RiverCellSavedData:
    cells = ConcurrentHashMap<RiverCellKey, RiverCell>

    getOrCompute(key, computeFunction):
        return cells.computeIfAbsent(key, k => {
            cell = computeFunction.apply(k)
            setDirty()
            return cell
        })
```

When two threads query the same uncached cell:
1. First thread enters computeIfAbsent and starts computation
2. Second thread blocks on the same key's segment lock
3. When computation completes, both threads receive the result
4. Only one computation happens per cell

**Why not Futures?** Empirical testing showed cell computation averages ~40-45ms. At this speed, `ConcurrentHashMap`'s segment locking is acceptable and the code is much simpler than managing Futures, executors, and cleanup. `setDirty()` is safe since SavedData's dirty flag is a simple boolean.

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

The refinement algorithm uses a 7×7 subcell grid. Why 7×7? An odd subdivision guarantees a true center subcell on each edge—entry and exit points land in subcell 3 (indices 0-6), not on a boundary between cells.

For each participating cell with inputs and outputs:

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

### Phase 9: Upstream Accumulation

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

### Phase 10: Worldgen Integration

**Goal:** Trigger cell computation automatically during chunk generation.

**Files:**
- `src/main/java/org/sosly/rivertale/mixin/ServerChunkCacheMixin.java` (or event handler)

**Hook point:**

During chunk generation, compute cells for any cell that overlaps the chunk being generated. This ensures cell data exists in the cache before the river carving system (a separate system outside this document) needs it.

```
onChunkGenerate(chunk):
    chunkWorldX = chunk.getPos().getMinBlockX()
    chunkWorldZ = chunk.getPos().getMinBlockZ()

    cellX = floor(chunkWorldX / CELL_SIZE)
    cellZ = floor(chunkWorldZ / CELL_SIZE)

    // getCell triggers computation if not cached
    cell = riverCellManager.getCell(new RiverCellKey(cellX, cellZ))
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

### Phase 11: Configuration

**Goal:** Expose tuning parameters for users.

**Files:**
- `src/main/java/org/sosly/rivertale/config/RiverConfig.java`

**Configurable Values:**

```
cellSize = 256                     // range: 128 to 4096
participationRate = 0.7            // range: 0.0 to 1.0
densityEqualityThreshold = 0.01
oceanThreshold = -0.13
lakeThreshold = 0.0                // depth below this is lake (terrain at/below y=63)
depthWeight = 1.0                  // how much terrain elevation influences flow direction
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
| 2 | `/rivertale cell` command exists, outputs placeholder data |
| 3 | `/rivertale locate` command exists with stub responses |
| 4 | Density values appear correctly in cell info |
| 5 | Output direction points toward lower density neighbor; locate commands work |
| 6 | Distance to ocean increases inland |
| 7 | Cells persist across save/load; no race conditions during parallel generation |
| 8 | River paths determined within cells; paths connect input to output edges |
| 9 | Upstream count reflects feeder topology |
| 10 | Cells computed automatically during chunk generation; `/rivertale cell` shows `[cached]` |
| 11 | Config changes affect new worlds |

---

## Consumer Interface

Once the cell-based network is complete, it provides the following data to downstream systems (river carving, decoration):

**Per-Cell Data:**
- `RiverCellKey` - coordinates (cellX, cellZ)
- `density` - averaged terrain density
- `classification` - LAND, OCEAN, COASTAL, LAKE, or LAKESHORE
- `primaryOutput` - direction water exits (N/S/E/W or NONE)
- `secondaryOutputs` - additional exit directions for new river sources
- `distanceToOcean` - cell count to nearest terminus (0 for all terminus types and basins)
- `upstreamCount` - feeders within limited depth
- `isBasin` - true if this cell is a basin terminus (endorheic, no outlet)
- `riverPath` - list of subcell coordinates the river passes through (for LAND, COASTAL, and LAKESHORE cells)

**Edge Centers:**

Rivers cross cell boundaries at edge centers only. World coordinates for edge centers:

```
getEdgeCenter(key, edge):
    baseX = key.worldX
    baseZ = key.worldZ

    NORTH: (baseX + CELL_SIZE/2, 0, baseZ)
    SOUTH: (baseX + CELL_SIZE/2, 0, baseZ + CELL_SIZE)
    EAST:  (baseX + CELL_SIZE, 0, baseZ + CELL_SIZE/2)
    WEST:  (baseX, 0, baseZ + CELL_SIZE/2)
    NONE:  null
```

Adjacent cells always agree on connection points because edge centers are defined by world coordinates, not cell-relative positions.

---

## Phase 12 (Optional): Multi-Pass Extension

This phase adds hierarchical river generation with multiple cell scales. It's not required for functional rivers but provides additional control over river hierarchy.

**When to consider this extension:**

- You want guaranteed major rivers at large scale with sparser tributaries at smaller scale
- You want forced tributary bounding (tributaries must drain to their parent cell's river)
- Single-pass rivers don't provide enough visual hierarchy

**What this phase adds:**

Multi-pass creates rivers at two (or more) scales:
- Pass 1: Large cells (e.g., 4096 blocks) for major rivers
- Pass 2: Smaller cells (e.g., ~585 blocks) for tributaries

Pass 2 tributaries are bounded by their parent Pass 1 cell—they cannot cross Pass 1 boundaries. This ensures all water within a Pass 1 cell either drains to that cell's river or collects in a local pond.

### Changes Required

**RiverCellKey:**

Add `pass` field to the record:

```
record RiverCellKey(int cellX, int cellZ, int pass)

getCellSize(pass):
    size = config.baseCellSize  // e.g., 4096
    for i from 1 to pass-1:
        size = size / config.subdivisionFactor  // e.g., 7
    return max(size, config.minimumCellSize)  // e.g., 128

fromBlockPos(blockX, blockZ, pass):
    cellSize = getCellSize(pass)
    return RiverCellKey(
        floorDiv(blockX, cellSize),
        floorDiv(blockZ, cellSize),
        pass
    )
```

**Participation Decay:**

Higher passes have lower participation rates:

```
isParticipating(key):
    baseRate = config.participationRate  // e.g., 0.7
    decayPerPass = config.participationDecay  // e.g., 0.25
    rate = baseRate * (1 - decayPerPass)^(key.pass - 1)

    seed = key.cellX * 31 + key.cellZ * 17 + key.pass * 7
    rng = seededRandom(seed XOR worldSeed)
    return rng.nextDouble() < rate
```

Example with 0.7 base rate and 0.25 decay:
- Pass 1: 70% participation
- Pass 2: 52.5% participation
- Pass 3: 39.4% participation

**Pass 2 Flow Calculation:**

Pass 2 cells only consider neighbors within the same Pass 1 cell:

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

getParentPass1Cell(pass2Key):
    pass1Size = getCellSize(1)
    pass2Size = getCellSize(2)
    worldX = pass2Key.cellX * pass2Size
    worldZ = pass2Key.cellZ * pass2Size
    return RiverCellKey(
        floorDiv(worldX, pass1Size),
        floorDiv(worldZ, pass1Size),
        1
    )
```

**Tributary Connection:**

Pass 2 tributaries flow toward lower density until they either:
1. Reach a subcell that contains the Pass 1 river path (merge point)
2. Reach a local minimum within the parent cell (becoming a small pond)

The Pass 1 river path (from Phase 8) runs through specific subcells. When a Pass 2 tributary's flow direction leads it into one of those subcells, it has reached the main river.

**Debug Command Updates:**

`/rivertale cell` shows both passes:

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

**Worldgen Integration Updates:**

Compute cells for all passes:

```
onChunkGenerate(chunk):
    chunkWorldX = chunk.getPos().getMinBlockX()
    chunkWorldZ = chunk.getPos().getMinBlockZ()

    for pass from 1 to config.maxPasses:
        cellSize = getCellSize(pass)
        cellX = floor(chunkWorldX / cellSize)
        cellZ = floor(chunkWorldZ / cellSize)

        cell = riverCellManager.getCell(new RiverCellKey(cellX, cellZ, pass))
```

**Configuration Additions:**

```
baseCellSize = 4096
subdivisionFactor = 7              // odd number ensures edge centers land in single subcell
minimumCellSize = 128
maxPasses = 2
participationDecay = 0.25          // range: 0.0 to 1.0
```

### Validation

1. Generate Pass 1 cells for an area
2. Generate Pass 2 cells within a Pass 1 cell
3. Verify Pass 2 cells flow toward lower density
4. Verify Pass 2 cells respect parent cell boundaries (no flow crossing Pass 1 edges)
5. Verify Pass 2 cells that reach local minima become basins (ponds)
6. Verify `/rivertale cell` shows both passes with independent flow directions

### Trade-offs

**Advantages:**
- Guaranteed major rivers at large scale
- Sparser tributaries (via participation decay)
- Forced tributary bounding ensures visual hierarchy

**Disadvantages:**
- More complex code
- Artificial constraint (tributaries can't naturally drain to closer rivers)
- Two passes to compute and cache per location

The single-pass architecture handles most cases well. Multi-pass is worth adding if testing reveals that upstream accumulation alone doesn't provide sufficient width/visual hierarchy, or if you want explicit control over which rivers are "major" vs "minor."
