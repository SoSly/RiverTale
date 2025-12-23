---
level: 5
parent: "[[Cell-based Networks Architecture]]"
status: implemented
---

# Cell-based Network Implementation Plan

This document outlines the build order for the region-based networking system described in `Cell-based Networks Architecture.md`. The goal is to implement in testable increments, with debug visualization available early.

## Key Decisions

These decisions were made during planning and supersede any conflicting details in the design doc:

| Decision | Value | Rationale |
|----------|-------|-----------|
| Region size | 384 blocks | Minimum size ensures 40-block river mouths fit wholly within a cell (384 / 8 = 48 blocks per cell) |
| Density source | Abstracted | Design provider interface, implement Lithosphere adapter first |
| Density formula | continents + (depth × 1.0) | Continents for ocean direction, depth weighted 1.0 for aggressive terrain-following |
| Debug visualization | Early | Add in Phase 2 for visual debugging throughout development |
| Density equality threshold | 0.01 | Prevents floating-point noise from causing unexpected flow |
| Participation rate | 0.7 | Default 70% of cells participate, configurable |
| Lake threshold | depth < 0 | Terrain at or below sea level (y=63) is water body; creates Body/Shore region types |

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
│                        RiverRegion                                │
│  - Coordinates (regionX, regionZ)                                   │
│  - Computed density, participation, outputs, distance            │
│  - Computes flow by comparing density to neighbors              │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                     RiverRegionManager                            │
│  - Computes regions on demand (per-level)                         │
│  - Handles region lookup and lazy computation                     │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   RiverRegionSavedData                            │
│  - Persists computed regions to world save                        │
│  - Extends SavedData like ContinentCacheSavedData               │
└─────────────────────────────────────────────────────────────────┘
```

## Implementation Phases

### Phase 1: Data Structures

**Goal:** Define core types without computation logic.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverRegion.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverRegionKey.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/FlowDirection.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/DensityProvider.java`

**RiverRegionKey:**

A record containing `regionX` and `regionZ`.

```
REGION_SIZE = 384  // constant (minimum size to fit 40-block river mouths in a cell)

fromBlockPos(blockX, blockZ):
    return RiverRegionKey(
        floorDiv(blockX, REGION_SIZE),
        floorDiv(blockZ, REGION_SIZE)
    )

worldX = regionX * REGION_SIZE
worldZ = regionZ * REGION_SIZE
centerX = worldX + REGION_SIZE / 2
centerZ = worldZ + REGION_SIZE / 2
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
    return RiverRegionKey(from.regionX + dx, from.regionZ + dz)
```

**RiverRegion:**

A class containing:
- `key`: RiverRegionKey
- `density`: double (averaged from cellDensities, immutable)
- `cellDensities`: double[8][8] (sampled for path refinement)
- `regionType`: RegionType enum (Basin, Divide, Fluvial, Shore, Barren, Body)
- `participating`: boolean (immutable)
- `primaryOutput`: FlowDirection
- `secondaryOutputs`: Set of FlowDirection
- `distanceToTerminus`: int (defaults to -1, meaning uncalculated)
- `riverPath`: List of cell coordinates (set by Phase 8)

Plus getters and NBT serialization. The `cellDensities` array is sampled once during region creation and reused for both averaging and path refinement.

**RegionType:**

An enum with six values:

- `Body` — Minecraft placed water here (ocean or lake); terminus with no outputs
- `Shore` — adjacent to Body regions; participates but terminus (river ends at water's edge)
- `Fluvial` — river passes through this region; normal flow rules apply
- `Divide` — flow originates here (local maximum in drainage network); river source
- `Basin` — flow-based local minimum where rivers collect; terminus with no outputs
- `Barren` — no visible water system; not participating in river network

Region cellType is determined during region creation by checking each cell against both thresholds. Ocean threshold is checked first (takes precedence), then lake threshold.

**DensityProvider:**

An interface with three methods:
- `getDensity(worldX, worldZ)` → double
- `getAveragedDensity(worldX, worldZ, regionSize)` → double (always uses 8×8 grid)
- `isOcean(density)` → boolean

**Validation:** Code compiles, records serialize to NBT correctly.

---

### Phase 2: Debug Command

**Goal:** Provide region data for debugging before implementing flow logic.

**Files:**
- `src/main/java/org/sosly/rivertale/command/RegionCommand.java`

**Command:**

```
/rivertale region
```

Prints information about the region at the player's current position:

```
Region (3, -7):
  Center: (1792, -3328)
  Density: 0.342
  Type: Fluvial
  Participating: true
  Output: SOUTH
  Inputs: NORTH, WEST
  Distance to terminus: 12
```

If a region is not participating, the output is simplified:

```
Region (24, -56):
  Participating: false
```

Ocean, coastal, and basin regions show their status:

```
Region (0, 12):
  Center: (256, 6400)
  Density: -0.21
  Type: Body
```

```
Region (2, 8):
  Center: (1280, 4352)
  Density: 0.08
  Type: Shore
  Participating: true
  Inputs: WEST
  Distance to terminus: 0
  River mouth at cell: (3, 4)
```

```
Region (5, 3):
  Center: (2816, 1792)
  Density: 0.67
  Type: Basin
  Inputs: SOUTH, EAST, WEST
```

**Validation:** Command exists, outputs placeholder data (hardcoded values before density provider is implemented).

---

### Phase 3: Locate Command

**Goal:** Provide a way to find nearby river network features, similar to vanilla's `/locate`.

**Files:**
- Modify `src/main/java/org/sosly/rivertale/command/RegionCommand.java` (or create separate `LocateCommand.java`)

**Command:**

```
/rivertale locate <feature>
```

**Available features (expanded as phases complete):**

| Feature | Available After | Description |
|---------|-----------------|-------------|
| `basin` | Phase 5 | Nearest region that is a basin terminus |
| `body` | Phase 5 | Nearest region of cellType Body (ocean or lake) |
| `shore` | Phase 5 | Nearest region of cellType Shore (river mouth to water) |
| `fluvial` | Phase 5 | Nearest region of cellType Fluvial (river passes through) |
| `divide` | Phase 5 | Nearest region of cellType Divide (river source) |
| `source` | Phase 5 | Nearest region with no inputs (river source) |
| `confluence` | Phase 5 | Nearest region with multiple inputs |

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
- For performance, can limit search radius or compute on-demand up to a limit

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

getAveragedDensity(worldX, worldZ, regionSize):
    sum = 0
    step = regionSize / 8.0
    for row from 0 to 7:
        for col from 0 to 7:
            sum += getDensity(worldX + col * step, worldZ + row * step)
    return sum / 64

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

All cells use a 8×8 subgrid (64 samples) regardless of cell size. This keeps the algorithm simple and ensures the same grid is available for both density averaging and path refinement.

With 384-block regions, each cell is 48 blocks (384 / 8 = 48). This ensures a 40-block wide river mouth fits wholly within a single cell.

**Integration with Phase 2 command:**

Once the density provider is implemented, update `/rivertale region` to display real density values instead of placeholder data. The command should now call `DensityProviderRegistry.getProvider(level)` and sample the region's averaged density.

**Validation:**
1. Place player in world
2. Run `/rivertale region` at various locations
3. Verify density value is reasonable (not NaN, within expected range)
4. Verify ocean detection works near coastlines
5. Verify inland locations have higher density than coastal locations
6. Verify mountain locations have higher density than adjacent valleys

---

### Phase 5: Flow Calculation

**Goal:** Implement the core flow algorithm.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverRegionManager.java`

**Flow Algorithm:**

```
computeFlow(region):
    // Region cellType was set during region creation based on cell densities
    // All terminus types have no output
    if region.regionType == Body:
        region.primaryOutput = NONE
        return

    if region.regionType == Shore:
        // Shore regions are terminuses—river ends at water's edge
        region.primaryOutput = NONE
        // But they still receive inputs from higher-density neighbors
        return

    // Fluvial/Divide regions: normal flow rules
    neighbors = getParticipatingNeighbors(region)
    lowerNeighbors = neighbors where neighbor.density < region.density - EPSILON

    if lowerNeighbors is empty:
        region.regionType = Basin
        region.primaryOutput = NONE
        return

    // Sort by density ascending
    sorted = lowerNeighbors.sortBy(density)

    region.primaryOutput = directionTo(sorted[0])
    region.secondaryOutputs = sorted[1..].map(directionTo)
```

**Coastal region handling:**

Coastal regions participate in the river network (they contain land) but are terminuses. They have no primaryOutput because the river ends at the coastline within the region, not at an edge center. They can still receive inputs from higher-density land neighbors—water flows into them and terminates at the ocean regions.

During path refinement (Phase 8), the path in a coastal region terminates when it reaches an ocean region rather than continuing to an edge center.

**Understanding secondary outputs:**

Secondary outputs are NOT water splitting. They are NEW RIVER SOURCES.

If a region has density 0.5 and two neighbors are lower (North at 0.3, South at 0.4), the region outputs primarily to North (main flow) and secondarily to South (new source). Water from upstream flows through this region to North. But this region is ALSO a local high point relative to South, so it's the SOURCE of a separate river flowing South. The region sits on a drainage divide.

See `Cell-based Networks.md` for a detailed diagram and explanation.

**Tiebreaker:**

When two neighbors have equal density (within EPSILON = 0.01):

```
breakTie(region, tied):
    seed = region.regionX * 31 + region.regionZ * 17
    rng = seededRandom(seed XOR worldSeed)
    return tied[rng.nextInt(tied.length)]
```

The tiebreaker is deterministic from coordinates and world seed.

**Participation Check:**

```
isParticipating(key):
    rate = config.participationRate  // default 0.7

    seed = key.regionX * 31 + key.regionZ * 17
    rng = seededRandom(seed XOR worldSeed)
    return rng.nextDouble() < rate
```

**Integration with Phase 2 command:**

Update `/rivertale region` to show region cellType, output direction, and inputs. These are computed on-the-fly by calling the flow algorithm.

**Validation:**
1. Generate cells in a test area
2. Run `/rivertale region` at various locations
3. Verify output direction points toward lower density
4. Verify cells near coastlines output toward ocean
5. Verify no region outputs toward a higher-density neighbor
6. Verify basins appear in local minima (no lower neighbor)

---

### Phase 6: Distance to Terminus

**Goal:** Implement lazy recursive distance calculation.

**Files:**
- Modify `RiverRegionManager.java`
- Modify `RiverRegion.java`

**Algorithm:**

```
getDistanceToTerminus(region):
    if region.distanceToTerminus >= 0:
        return region.distanceToTerminus  // already computed

    // All terminuses have distance 0
    if region.regionType in [Body, Shore]:
        region.distanceToTerminus = 0
        return 0

    if region.regionType == Basin:
        region.distanceToTerminus = 0
        return 0

    downstream = getRegion(region.primaryOutput.neighbor(region.key))
    region.distanceToTerminus = 1 + getDistanceToTerminus(downstream)
    return region.distanceToTerminus
```

**Recursion Safety:**

The acyclic guarantee from the design doc ensures recursion terminates. Flow always moves toward lower density, and density comparisons are transitive. However, we add a safety limit:

```
MAX_RECURSION_DEPTH = 1000

getDistanceToTerminus(region, depth):
    if depth > MAX_RECURSION_DEPTH:
        log warning "Distance calculation exceeded max depth at {region.key}"
        return depth
    // ... rest of algorithm
```

This catches bugs during development without infinite loops.

**Integration with Phase 2 command:**

Update `/rivertale region` to include "Distance to terminus" in the output. This value comes from `getDistanceToTerminus()` and represents how many regions away from a terminus (ocean or basin) this region is.

**Validation:**
1. Run `/rivertale region` on a coastal region
2. Verify distance is 0 (coastal regions are terminuses)
3. Run on a land region adjacent to coastal
4. Verify distance is 1
5. Run on an inland region further from coast
6. Verify distance increases with distance from coast
7. Verify basin regions have distance 0

---

### Phase 7: River Path Refinement

**Goal:** Determine the actual path each river takes within its region.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverPathRefiner.java`
- Modify `RiverRegion.java` to store path data

**Why this is needed:**

A region's flow direction (e.g., "outputs EAST") tells you WHERE water exits, but not the PATH it takes to get there. A region with west input and east output could have a straight river, an S-curve, or a meandering path. Without knowing the path, the carving system doesn't know where to place blocks.

**Algorithm:**

The refinement algorithm uses an 8×8 cell grid (64 cells per region).

For each participating region with inputs and outputs:

1. Identify entry cell(s) — which cell(s) contain the input edge center(s)
2. Identify exit cell — which cell contains the output edge center
3. Sample density at each cell
4. Apply exit-direction weighting to create a virtual gradient toward the exit
5. Pathfind using weighted costs

**Why weighting is necessary:**

Raw terrain density doesn't conveniently slope toward the region's exit. A region might have its lowest density in the southwest corner while the exit is to the east. Without weighting, the river would flow southwest and never reach the exit.

The weighting adds a penalty based on distance from the exit:

```
weighted_cost = raw_density + (manhattan_distance_to_exit * weight_factor)
```

A weight factor of ~0.05 is enough to ensure eastward progress while still allowing density-driven meandering. See `Cell-based Networks.md` for a worked example with actual numbers.

```
refineRiverPath(region):
    if not region.participating or region.regionType == Body or region.regionType == Basin:
        return  // No path needed

    entry = getCellAtEdge(region, region.inputDirections[0])  // e.g., (3,0) for west

    // Get the 8x8 density grid (already sampled during region density averaging)
    rawDensities = region.cellDensities  // sampled during region creation

    if region.regionType == Shore:
        // Coastal regions: path terminates at first ocean cell
        region.riverPath = findPathToOcean(entry, rawDensities)
    else:
        // Land regions: path goes to exit edge center
        exit = getCellAtEdge(region, region.primaryOutput)  // e.g., (3,6) for east
        costs = applyExitWeighting(rawDensities, exit)
        region.riverPath = findPath(entry, exit, costs, rawDensities)

applyExitWeighting(rawDensities, exit):
    costs = empty 8x8 grid
    for each cell (row, col):
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
    // For coastal regions: follow density gradient until reaching ocean region
    path = [entry]
    visited = {entry}
    current = entry

    while true:
        // Check if current cell is ocean
        if rawDensities[current] < OCEAN_THRESHOLD:
            return path  // River has reached the sea

        neighbors = getAdjacentUnvisited(current, visited)

        if neighbors is empty:
            // Shouldn't happen in a coastal region, but handle gracefully
            return path

        // Move toward lowest density (toward ocean)
        next = neighbors.minBy(n => rawDensities[n])
        path.add(next)
        visited.add(next)
        current = next

    return path
```

**Coastal path refinement:**

Coastal regions don't have an exit edge center—the river ends at the coastline within the region. The `findPathToOcean` algorithm follows the raw density gradient (no exit weighting needed) until it reaches a cell below the ocean threshold. That cell is where the river meets the sea.

**Path representation:**

The path is stored as a list of cell coordinates. The carving system interpolates between these to place actual river blocks.

**Multiple inputs (confluence):**

If a region has multiple inputs, run pathfinding from each input independently. Track visited cells across all paths. When a path enters a cell already visited by another path, the paths merge at that point—the later path joins the earlier path's route from there to the exit.

If paths don't intersect until the exit itself, that's fine. Two streams entering from opposite sides and both reaching the exit edge is a valid confluence—they merge at the output.

**Output:**

Each participating region now has:
- `riverPath` — ordered list of cells the river passes through
- Entry point(s) and exit point in cell coordinates

**Validation:**
1. Run `/rivertale region` on a participating region
2. Verify path data is present
3. Verify path connects input edge to output edge
4. Verify path prefers lower-density cells (visual inspection of debug output)

---

### Phase 8: Upstream Accumulation

**Goal:** Implement limited-depth upstream counting for width calculation.

**Files:**
- Modify `RiverRegion.java`
- Modify `RiverRegionManager.java`

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

**Note:** The depth limit (`upstreamDepthLimit` in config, default 3) keeps computation cheap. The count only needs to distinguish "few" from "many" for width calculation—higher limits provide more granularity at the cost of more region queries.

**Width Derivation:**

The river feature generator will use upstream count to determine width:

| Upstream Count | Width Category |
|----------------|----------------|
| 0 | Source stream |
| 1-2 | Minor tributary |
| 3-5 | Regional river |
| 6+ | Major river |

Actual block widths are the feature generator's concern, not the region system's.

**Validation:**
1. Find a region with multiple upstream feeders
2. Run `/rivertale region` and verify upstream count
3. Verify count is limited by depth (doesn't propagate infinitely)
4. Verify a source region (no inputs) has upstream count 0

---

### Phase 9: Worldgen Integration

**Goal:** Trigger region computation automatically during chunk generation.

**Files:**
- `src/main/java/org/sosly/rivertale/mixin/ServerChunkCacheMixin.java` (or event handler)

**Hook point:**

During chunk generation, compute regions for any region that overlaps the chunk being generated. This ensures region data is available when the river carving system (a separate system outside this document) needs it.

```
onChunkGenerate(chunk):
    chunkWorldX = chunk.getPos().getMinBlockX()
    chunkWorldZ = chunk.getPos().getMinBlockZ()

    regionX = floor(chunkWorldX / REGION_SIZE)
    regionZ = floor(chunkWorldZ / REGION_SIZE)

    region = riverRegionManager.getRegion(new RiverRegionKey(regionX, regionZ))
```

**When to trigger:**

Hook into chunk generation after biome placement but before feature generation. The region data needs to exist before any river carving features run.

For Forge 1.20.1, this could be:
- A mixin to `ServerChunkCache` or `ChunkGenerator`
- The `ChunkEvent.Load` event (though this fires after generation completes)

The exact hook point depends on when river carving will run, which is outside this document's scope.

**What this phase does NOT do:**

- Carve terrain
- Place water blocks
- Modify biomes
- Generate any visible features

This phase only ensures region network data is computed during worldgen. A separate river carving system consumes this data to actually shape the terrain.

**Validation:**
1. Create a new world
2. Walk around to generate chunks
3. Run `/rivertale region` — verify computed data is consistent
4. Check logs for region computation messages during chunk generation
5. Verify no duplicate computations for the same region

---

### Phase 10: Configuration

**Goal:** Expose tuning parameters for users.

**Files:**
- `src/main/java/org/sosly/rivertale/config/RiverConfig.java`

**Configurable Values:**

```
regionSize = 384                     // range: 384 to 4096 (minimum ensures 40-block mouths fit in cells)
participationRate = 0.7            // range: 0.0 to 1.0
densityEqualityThreshold = 0.01
oceanThreshold = -0.13
lakeThreshold = 0.0                // depth below this is lake (terrain at/below y=63)
depthWeight = 1.0                  // how much terrain elevation influences flow direction
upstreamDepthLimit = 3             // how many cells upstream to count for width
```

**Note:** Since regions are computed deterministically from world seed and coordinates, configuration changes will affect all regions consistently.

**Validation:**
1. Modify config values
2. Generate new world
3. Verify cells reflect new configuration
4. Verify existing worlds are unaffected

---

## Testing Milestones

| After Phase | Testable Behavior |
|-------------|-------------------|
| 2 | `/rivertale region` command exists, outputs placeholder data |
| 3 | `/rivertale locate` command exists with stub responses |
| 4 | Density values appear correctly in region info |
| 5 | Output direction points toward lower density neighbor; locate commands work |
| 6 | Distance to terminus increases inland |
| 7 | Regions computed deterministically; same inputs produce same outputs |
| 8 | River paths determined within cells; paths connect input to output edges |
| 9 | Upstream count reflects feeder topology |
| 9 | Regions computed automatically during chunk generation |
| 11 | Config changes affect new worlds |

---

## Consumer Interface

Once the region-based network is complete, it provides the following data to downstream systems (river carving, decoration):

**Per-Region Data:**
- `RiverRegionKey` - coordinates (regionX, regionZ)
- `density` - averaged terrain density
- `regionType` - Basin, Divide, Fluvial, Shore, Barren, or Body
- `primaryOutput` - direction water exits (N/S/E/W or NONE)
- `secondaryOutputs` - additional exit directions for new river sources
- `distanceToTerminus` - region count to nearest terminus (0 for all terminus types and basins)
- `upstreamCount` - feeders within limited depth
- `riverPath` - list of cell coordinates the river passes through (for Fluvial, Divide, and Shore regions)

**Edge Centers:**

Rivers cross region boundaries at edge centers only. World coordinates for edge centers:

```
getEdgeCenter(key, edge):
    baseX = key.worldX
    baseZ = key.worldZ

    NORTH: (baseX + REGION_SIZE/2, 0, baseZ)
    SOUTH: (baseX + REGION_SIZE/2, 0, baseZ + REGION_SIZE)
    EAST:  (baseX + REGION_SIZE, 0, baseZ + REGION_SIZE/2)
    WEST:  (baseX, 0, baseZ + REGION_SIZE/2)
    NONE:  null
```

Adjacent regions always agree on connection points because edge centers are defined by world coordinates, not region-relative positions.

