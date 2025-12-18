---
level: 3
parent: "[[Rivers Feature Concept]]"
status: review
---

Rivers use a cell-based networking system to determine flow direction, connectivity, and elevation across the world.

## Cell Structure

The world divides into a grid of square cells. Each cell has:

1. A **density value** representing how inland the location is
2. **Four cardinal neighbors** (north, south, east, west)
3. **Edge centers** where rivers enter and exit the cell
4. A **participation flag** determining whether this cell is part of the river network

Cells only consider cardinal neighbors (N/S/E/W) for flow—diagonal flow is not supported.

Density represents distance from ocean. High density = continental interior. Low density = coastal. Below-threshold density = ocean. Density differences below 0.01 are treated as equal to prevent floating-point noise from causing unexpected flow directions in flat terrain. When neighbors have equal density, a seeded random tiebreaker determines flow direction.

**Density sampling:**

A cell's density is averaged across a 7×7 subgrid of sample points, not taken from a single center point. This captures the cell's overall terrain character rather than an arbitrary point that might be anomalous (a mountain peak, a local depression, etc.).

The same 7×7 grid is reused for river path refinement, so the density values sampled here are cached and available for pathfinding within the cell.

**Participation:**

Not every cell contains a river. Each cell uses a seeded random check to determine whether it participates in the river network. Non-participating cells are invisible to the system—they have no inputs, no outputs, and water neither flows through them nor around them.

Participating cells only consider other participating cells when comparing densities. If a cell's only lower-density neighbor is non-participating, that cell becomes a basin (local minimum from the river system's perspective).

Participation rate is a configurable percentage, independent of cell size. This allows tuning river density separately from river detail:

| Cell Size | Participation | Result |
|-----------|---------------|--------|
| Large | Any | Major rivers only, sparse network |
| Small | Low | Detailed river paths, sparse network |
| Small | High | Dense, highly branched river network |

## Flow Determination

Each cell determines its flow by comparing its density to its participating neighbors. Non-participating neighbors are ignored entirely. This comparison happens once, on first access, and the result is cached permanently.

**Outputs:**

A cell outputs water toward participating neighbors with lower density. The lowest-density participating neighbor becomes the primary exit. Water from upstream cells flows through this cell and exits through that edge's center point.

**Secondary outputs are new river sources, not splits.**

If multiple participating neighbors have lower density, the cell marks secondary outputs toward each additional neighbor. These secondary outputs represent *new rivers originating from this cell*, not water splitting from the main flow.

Example: Cell A has density 0.5. Its neighbors are North (0.3), South (0.4), East (0.6), West (0.7).

```
        North (0.3)
            ↑
            │ primary output
            │
West ──→  Cell A  ←── East
(0.7)     (0.5)      (0.6)
            │
            │ secondary output (NEW SOURCE)
            ↓
        South (0.4)
```

- Primary output = North (lowest density neighbor)
- Secondary output = South (also lower than A, but not the lowest)
- Water from East and West flows through A and exits North
- A is ALSO a local high point relative to South, so A is a **source** for a separate river flowing South

The river flowing South didn't come from East or West—it *originates* at A. Cell A sits on a drainage divide: water falling on A's north side flows north; water falling on A's south side flows south.

**Basins:**

If no participating neighbor has lower density and the cell isn't ocean, the cell is a local minimum. Water pools here. This is a basin—an endorheic terminus with no outlet.

**Inputs:**

A cell receives water from participating neighbors with higher density. Each higher-density participating neighbor means an inflow point at that shared edge's center.

Multiple inflows each pathfind toward the exit. When paths intersect, they merge at that point and share the remaining route. If paths don't intersect until the exit, they merge there—two streams dumping into the same output edge is a valid confluence.

**Cell classification:**

Cells are classified based on their subcell densities:

- **Land** — all subcells above ocean threshold; normal flow rules apply
- **Ocean** — all subcells below ocean threshold; cell is a terminus, no river passes through
- **Coastal** — mixed subcells; cell participates in river network but is a terminus

Classification is determined during cell creation by checking the 7×7 subcell densities against the ocean threshold. No additional sampling required.

**Ocean termination:**

Ocean cells are terminuses. They have no outputs. Rivers entering ocean cells end at the coastline.

**Coastal cells:**

A coastal cell straddles the boundary between land and ocean. Some subcells are above the ocean threshold (land), others are below (ocean).

Coastal cells participate in the river network—they contain land where rivers can flow. But they are also terminuses: water flows in and reaches the ocean within this cell. The river's effective exit is wherever the path first touches an ocean subcell, not the cell's edge center.

Coastal cells cannot receive input from ocean-side edges. Rivers don't flow in from the sea.

During path refinement, ocean subcells act as terminuses. When the pathfinding algorithm reaches an ocean subcell, the path ends there. The river has reached the sea.

```
Example coastal cell (ocean threshold = -0.13):

       0     1     2     3     4     5     6
    ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┐
  0 │ .42 │ .38 │ .25 │-.15 │-.22 │-.31 │-.40 │
    ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
  1 │ .45 │ .41 │ .30 │-.10 │-.18 │-.28 │-.35 │
    ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
  2 │ .48 │ .44 │ .35 │ .05 │-.12 │-.20 │-.30 │
    ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
  3 │ .50 │ .46 │ .38 │ .15 │-.05 │-.15 │-.25 │  ← entry at (3,0)
    ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
  4 │ .47 │ .43 │ .36 │ .08 │-.08 │-.18 │-.28 │
    ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
  5 │ .44 │ .40 │ .32 │-.02 │-.14 │-.24 │-.32 │
    ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
  6 │ .40 │ .36 │ .28 │-.10 │-.20 │-.30 │-.38 │
    └─────┴─────┴─────┴─────┴─────┴─────┴─────┘
                        ↑
              columns 4-6 are ocean (< -0.13)

River enters from west at (3,0). Path terminates when it first
reaches an ocean subcell—perhaps (3,4) or (2,4). The river mouth
forms at the actual coastline, not at an arbitrary edge center.
```

This classification provides a hook for future estuary and delta features. A coastal cell carries the context those systems need: subcell-level coastline geography, upstream accumulation, and the specific subcells where river meets ocean.

## Edge Centers

Rivers cross cell boundaries at edge centers only. The north edge center of cell (0, 0) is always at the same world coordinates, regardless of which cell generated first.

This constraint ensures adjacent cells always agree on connection points. Cell A says "I exit through my south edge center." Cell B says "I receive input from my north edge center." Those are the same point in world coordinates.

## River Path Within a Cell

Knowing a cell's inputs and outputs tells you WHERE water enters and exits, but not the PATH it takes in between. A cell with west input and east output could have a straight river, an S-curve, or a path that dips south before curving back. The path matters for carving.

**Refinement step:**

After a pass completes (all cells have determined their flow direction), a refinement algorithm runs to determine the actual river course within each participating cell. This uses a 7×7 subcell grid to sample density and route the river toward lower-density areas while connecting the required entry and exit points.

Why 7×7? An odd subdivision guarantees a true center subcell on each edge. With 7×7, the edge center is always subcell 3 (indices 0-6). Entry and exit points land cleanly in a single subcell, making flow decisions straightforward.

```
Pass 1 cell with input from WEST, output to EAST:

Step 1: Raw density samples. Note the terrain doesn't slope nicely
toward the exit—there's a ridge in column 3 and the lowest point
is in the southwest corner at (6,1).

Raw density (row, col):
         0     1     2     3     4     5     6
      ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┐
    0 │ .62 │ .58 │ .55 │ .72 │ .68 │ .61 │ .57 │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    1 │ .55 │ .48 │ .46 │ .69 │ .59 │ .52 │ .50 │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    2 │ .51 │ .39 │ .55 │ .65 │ .53 │ .47 │ .45 │ ← depression at (2,1)
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    3 │ .48 │ .43 │ .47 │ .70 │ .58 │ .51 │ .55 │ ← entry (3,0) / exit (3,6)
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    4 │ .44 │ .40 │ .45 │ .63 │ .54 │ .50 │ .53 │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    5 │ .38 │ .36 │ .42 │ .58 │ .52 │ .49 │ .51 │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    6 │ .33 │ .31 │ .39 │ .55 │ .50 │ .48 │ .50 │ ← raw lowest at (6,1)
      └─────┴─────┴─────┴─────┴─────┴─────┴─────┘

Step 2: Apply exit-direction weight. Without this, water would flow
southwest and never reach the exit. The weight penalizes cells that
are far from the exit, creating a "virtual gradient" toward it.

Weight = distance_to_exit * 0.05
Weighted cost = raw_density + weight

Distance to exit (3,6) using Manhattan distance:
         0     1     2     3     4     5     6
      ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┐
    0 │  9  │  8  │  7  │  6  │  5  │  4  │  3  │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    1 │  8  │  7  │  6  │  5  │  4  │  3  │  2  │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    2 │  7  │  6  │  5  │  4  │  3  │  2  │  1  │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    3 │  6  │  5  │  4  │  3  │  2  │  1  │  0  │ ← exit at distance 0
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    4 │  7  │  6  │  5  │  4  │  3  │  2  │  1  │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    5 │  8  │  7  │  6  │  5  │  4  │  3  │  2  │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    6 │  9  │  8  │  7  │  6  │  5  │  4  │  3  │
      └─────┴─────┴─────┴─────┴─────┴─────┴─────┘

Weighted cost = density + (distance * 0.05):
         0     1     2     3     4     5     6
      ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┐
    0 │ 1.07│ .98 │ .90 │ 1.02│ .93 │ .81 │ .72 │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    1 │ .95 │ .83 │ .76 │ .94 │ .79 │ .67 │ .60 │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    2 │ .86 │ .69 │ .70 │ .85 │ .68 │ .57 │ .50 │ ← (2,1) now .64
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    3 │ .78 │ .68 │ .67 │ .85 │ .68 │ .56 │ .55 │ ← entry/exit row
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    4 │ .79 │ .70 │ .70 │ .83 │ .69 │ .60 │ .58 │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    5 │ .78 │ .71 │ .72 │ .83 │ .72 │ .64 │ .61 │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    6 │ .78 │ .71 │ .74 │ .85 │ .75 │ .68 │ .65 │
      └─────┴─────┴─────┴─────┴─────┴─────┴─────┘

Now the weighted lowest is near the exit, not the southwest corner.
The depression at (2,1) creates a local trap that the algorithm must escape.

Step 3: Pathfind using weighted costs with backtracking.

Algorithm:
  0. If an adjacent cell is the exit, move there and terminate
  1. From current cell, find adjacent unvisited cells with lower cost
  2. If any exist, move to the lowest
  3. If none exist (stuck at local minimum), move to the lowest-cost
     unvisited cell that is closer to the exit than current
  4. If ALL adjacent unvisited cells are farther from exit (dead end),
     backtrack to the previous cell and continue from step 1
  5. Repeat until exit reached

Trace:
  (3,0) cost=.78 → (3,1)=.68 lower ✓
  (3,1) cost=.68 → (3,2)=.67 lower ✓
  (3,2) cost=.67 → (2,2)=.66 lower ✓
  (2,2) cost=.66 → (2,1)=.69 lower ✓
  (2,1) cost=.69 → stuck, no lower unvisited
        Neighbors: (1,1)=.83, (2,0)=.86, (2,2)=visited — none lower
        Step 3: find unvisited closer to exit than distance 6
        (1,1) is distance 7, (2,0) is distance 7 — both farther
        Dead end! Backtrack to (2,2)
  (2,2) cost=.66 → (2,1) already visited, retry from here
        Remaining unvisited neighbors: (1,2)=.76, (2,3)=.85 — none lower
        Step 3: find unvisited closer to exit than distance 5
        (1,2) is distance 6 — farther
        (2,3) is distance 4 — closer! Move to (2,3)=.85 ✓
  (2,3) cost=.85 → (2,4)=.68 lower ✓
  (2,4) cost=.68 → (2,5)=.57 lower ✓
  (2,5) cost=.57 → (2,6)=.50 lower ✓
  (2,6) cost=.50 → (3,6) is adjacent exit, done ✓

Resulting path:
         0     1     2     3     4     5     6
      ┌─────┬─────┬─────┬─────┬─────┬─────┬─────┐
    0 │     │     │     │     │     │     │     │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    1 │     │     │     │     │     │     │     │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    2 │     │  x  │  *  │  *  │  *  │  *  │  *  │ ← x = backtracked
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    3 │  *  │  *  │  *  │     │     │     │  *  │ ← entry/exit row
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    4 │     │     │     │     │     │     │     │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    5 │     │     │     │     │     │     │     │
      ├─────┼─────┼─────┼─────┼─────┼─────┼─────┤
    6 │     │     │     │     │     │     │     │
      └─────┴─────┴─────┴─────┴─────┴─────┴─────┘
    ↑                                         ↑
  entry                                     exit
  (3,0)                                     (3,6)

Final path: (3,0)→(3,1)→(3,2)→(2,2)→(2,3)→(2,4)→(2,5)→(2,6)→(3,6)
(The detour to (2,1) was backtracked and is not part of the final path.)

The river follows the weighted gradient east, dips into the depression
at (2,1), discovers it's a dead end, backtracks to (2,2), then forces
its way across the ridge at (2,3) because that's the only direction
closer to the exit. Once past the ridge, the path follows lower costs
smoothly to the exit.
```

The refinement grid is the same 7×7 subdivision used for Pass 2 tributaries. This serves two purposes:
1. Determines the actual path the main river takes
2. Provides the grid that Pass 2 tributaries flow toward

**Constraint:** The path must connect the entry edge center to the exit edge center. The algorithm finds the lowest-density route between these fixed endpoints, not a free-form path.

**Output:** A sequence of subcells that the river passes through. The carving system uses this sequence to place the actual river blocks.

## Distance to Ocean

Rivers descend toward the ocean. Elevation derives from distance-to-ocean measured in cells.

**Calculation:**

When a cell determines its primary output, it recursively queries the downstream neighbor for that neighbor's distance to ocean. The cell's distance is one plus the downstream distance. Ocean cells return distance zero.

```
Cell F outputs to Cell E.
Cell E outputs to Cell D.
Cell D is ocean.

F asks E: "What's your distance?"
E asks D: "What's your distance?"
D responds: "Zero. I'm ocean."
E calculates: 0 + 1 = 1. Returns 1 to F.
F calculates: 1 + 1 = 2. Caches distance 2.
```

**Caching:**

Each cell calculates its distance once. The result caches permanently. Subsequent queries—whether from upstream cells or from chunk generation—return the cached value immediately.

The recursion only happens on first access. After that, queries are instant lookups.

## Upstream Accumulation

Cells track upstream feeders to inform width calculation. A cell with many upstream feeders carries more water and should be wider.

When a cell needs its upstream count, it queries its neighbors: "do you flow into me?" For each neighbor that does, the cell recursively counts that neighbor's upstream feeders, up to a limited depth (a few steps). Beyond that depth, additional upstream complexity doesn't meaningfully affect local width.

This limited-depth counting avoids the ordering problem that would occur with permanent registration. A cell's upstream count is computed on demand from its immediate neighborhood, not accumulated from exploration history.

The count distinguishes "few" from "many" upstream feeders, which is sufficient for width calculation. A cell receiving flow from one upstream cell is narrower than one receiving flow from several, even if those several cells share common ancestors further upstream.

Note that upstream count only includes cells that flow *to this cell*. If cell A has upstream feeders and flows to cell C as its primary output, cell B (receiving A as a secondary output) only counts A itself—not A's upstream feeders. This is correct because the river flowing from A to B is a *new source* originating at A. The water from A's upstream feeders flows to C, not to B. B's river started at A, so A is its only upstream cell.

## Emergent Behaviors

**Tributaries:**

When multiple cells output to the same downstream cell, their rivers merge at that cell. No explicit tributary logic required—branching emerges from the grid structure.

Smaller cells = more boundaries = more merge points = more branching.

**Endorheic basins:**

When all neighbors have higher density, the cell has no output. Water collects here naturally. The cell becomes a basin terminus, analogous to an ocean terminus but inland.

**Determinism:**

Flow direction derives from density comparisons. Density derives from world seed. Therefore, flow is deterministic from seed alone.

The first cell to query a downstream chain triggers the recursive calculation, but the results are identical regardless of who asked first. Cell E's output direction depends on Cell E's neighbors' densities, not on whether Cell F or Cell B triggered the query.

**Acyclic guarantee:**

The flow graph cannot contain cycles. Every cell outputs toward lower density, and density comparisons are transitive. If A flows to B, then B's density is lower than A's. B cannot flow back to A because A has higher density. This property is fundamental to the algorithm—the recursive distance-to-ocean query terminates because it always moves toward lower density until reaching ocean or a basin terminus.

## River Feature Generation

Once a cell knows its inputs, outputs, distance, and upstream count, it provides this data to the river feature generator. The cell system determines *connectivity*. Feature generation determines *appearance*.

**What cells provide:**

- Input edge centers (where water enters)
- Output edge center (where water exits)
- Distance to ocean (for elevation calculation)
- Upstream count (for width calculation)
- Whether this cell is a source (no inputs, higher than all neighbors)
- Whether this cell is a basin terminus (no outputs, not ocean)

**What cells don't determine:**

- The specific path rivers take within the cell
- River width in blocks
- Terrain carving depth
- Water surface elevation in world coordinates
- Visual features like rapids, meanders, or waterfalls

Those concerns belong to separate systems that consume cell data.

**Consumer interface:**

Cell-based Networks provides connectivity data. A separate river carving and decoration system is responsible for querying cells and placing actual blocks during chunk generation. How that system determines which cells to query, how it interpolates river paths within cells, and how it coordinates with Minecraft's chunk generation pipeline are outside this document's scope. The boundary is the data cells provide; the consumption of that data is another system's concern.

## Caching Strategy

**What to cache per cell:**

- Participation flag
- Primary output direction (N/S/E/W or none)
- Secondary output directions (for tributary sources)
- Distance to ocean
- Basin terminus flag

Upstream feeder count is not cached. It's computed on demand via limited-depth traversal each time it's needed. This keeps the count accurate regardless of exploration order, and the computation is cheap (only a few steps up).

**When to cache:**

On first access. A cell's data never changes after initial calculation.

**Cache persistence:**

The cache survives world save/load. Once calculated, a cell's networking data is permanent for that world.

**Cache invalidation:**

None. Cell data derives from seed-based density values that never change. There's no scenario where cached data becomes stale.

## Performance Characteristics

**First access:**

Querying an uncached cell triggers recursive downstream queries until reaching ocean. In the worst case (cell at maximum distance from ocean), this touches every cell along the path.

However, each cell in the chain caches its result. The next query to any cell in that chain is instant.

**First-access cost:**

The worst-case first access is expensive. A player spawning mid-continent triggers a chain that walks potentially 50+ cells downstream before returning. This happens synchronously during the first chunk generation request that needs that cell.

Mitigations:

1. **Chain length is bounded.** With 4096-block cells, even a massive continent 100km across is only ~25 cells. The actual worst case is "one continent's worth of cells," not infinite.

2. **Cell computation is cheap.** Each cell in the chain does density sampling (already computed by the terrain system) and neighbor comparison. No pathfinding, no flood fill.

3. **Chains cache aggressively.** Once any cell in the chain is computed, queries from further upstream stop there. Exploration from the coast pre-warms the cache.

4. **Distance computation is separable.** Flow direction and participation can be computed without distance. If first-access proves problematic during testing, distance calculation can be deferred until river carving actually needs it—by which point more of the downstream chain may be cached.

If profiling shows this is still a problem, the implementation can compute distance asynchronously and block only when carving actually needs the value. But this is an optimization to consider during implementation, not a design change.

**Subsequent access:**

Cache lookup only. No recursion, no density sampling, no neighbor comparison.

**Parallel exploration:**

Multiple threads may query cells concurrently during chunk generation. When two threads query the same uncached cell simultaneously, one thread performs the calculation while others block waiting for the result. The cache serves as the synchronization point—once a cell is cached, all subsequent queries return immediately without blocking.

The recursive distance-to-ocean query may touch many cells in sequence, but each cell in the chain is computed and cached independently. Threads only contend on individual cells, not entire query chains.

**Memory footprint:**

Each cached cell stores: key (3 ints), density (double), flags (booleans), output directions (enum references), and distance (int). With Java object overhead, this is roughly 80-120 bytes per cell.

With 4096-block cells, a world explored to 10 million blocks radius contains ~2500 cells per axis, or ~6 million cells total. At 100 bytes per cell, that's ~600MB. In practice, most worlds won't be explored that far, and Pass 2 cells (512 blocks) only exist where players have actually generated chunks.

**World borders:**

Minecraft worlds have a configurable border (default ±30 million blocks). Cells near or beyond the border have missing neighbors in some directions.

This is a non-issue for flow calculation. The density function works for any coordinates—even beyond the world border where no chunks will ever generate. When a cell at the world edge needs to compare density to a neighbor "outside" the border, it samples that neighbor's density normally. If the outside cell has lower density, water flows that direction (toward an ocean that will never be visited). If higher, water flows inward.

The cell network doesn't care whether chunks exist. It only cares about density values, which are computable anywhere. Rivers near the world border may flow toward unreachable oceans, but they'll still flow correctly toward lower density. Players at the border won't see discontinuities—just rivers heading off toward distant water.

## Multi-Pass Generation

River networks can be generated in multiple passes at different scales. Each pass subdivides the previous pass's cells and adds detail within the constraints established by the parent.

**Cells are nesting dolls:**

Every block position in the world exists inside exactly one cell per pass. With multiple passes, these cells nest—a Pass 2 cell is entirely contained within a single Pass 1 cell, a Pass 3 cell is entirely contained within a single Pass 2 cell, and so on.

```
Pass 1 cell (4096 blocks):
┌─────────────────────────────────────────────────┐
│                                                 │
│    Pass 2 cell (512 blocks):                    │
│    ┌─────────┐                                  │
│    │ You are │                                  │
│    │  here   │                                  │
│    └─────────┘                                  │
│                                                 │
│                                                 │
└─────────────────────────────────────────────────┘
```

Standing at any location, you can ask "what cell am I in?" and get one answer per pass. The Pass 1 answer covers a huge region. The Pass 2 answer covers a smaller region entirely inside that Pass 1 cell. Each successive pass gives a more local answer, but all answers nest within each other.

**Configuration:**

Users configure three values:

- `CELL_SIZE` - Base cell size for Pass 1
- `PASSES` - Requested number of subdivision passes
- `DENSITY` - Base participation rate from 0.0 to 1.0

Each pass divides cell size by a fixed subdivision factor. A minimum cell size prevents subdivision from creating cells too small for meaningful flow decisions. If the requested passes would produce cells below the minimum, the system uses fewer passes than requested.

Example: if the base cell size is 4096 blocks with a subdivision factor of 8 and minimum of 128 blocks:
- Pass 1: 4096 blocks
- Pass 2: 512 blocks
- Pass 3: 64 blocks ← below minimum, rejected

The system performs 2 passes. Larger base cell sizes allow more passes before hitting the minimum.

Participation rate decreases with each pass using a decay factor, so main rivers are reliably present while tributaries are sparser. The specific decay factor is a tuning parameter.

**Pass hierarchy and execution order:**

**Passes execute sequentially. Later passes add detail on top of earlier passes—they never influence or modify earlier passes.**

Pass 1 establishes the major river network: which cells have rivers, where they flow, where they connect. This is the "main river system." Pass 1 completes fully before Pass 2 begins.

Pass 2 adds tributaries within each Pass 1 cell. These are smaller streams that feed INTO the Pass 1 river, not subdivisions OF it. Pass 2 cells use density comparison to determine where tributaries form and which direction they flow, but the Pass 1 river path is already fixed—Pass 2 cannot change it.

Think of it like this:
- Pass 1 draws the main highways on a map
- Pass 2 draws the side roads that connect to those highways
- The side roads don't move the highways; they just provide more ways to reach them

**What Pass 2 cells do:**

Pass 2 cells within a Pass 1 cell determine where tributaries form. Each Pass 2 cell uses density comparison against its Pass 2 neighbors to find its flow direction. Tributaries flow toward lower density, eventually reaching either:
1. The Pass 1 river path (merging into it), or
2. A local minimum (forming a pond unconnected to the main river)

**Pass 2 cells are bounded by their parent Pass 1 cell.**

A Pass 2 cell only considers neighbors within the same Pass 1 cell. Pass 2 tributaries cannot cross Pass 1 cell boundaries. This ensures that all water within a Pass 1 cell either:
- Drains to that cell's Pass 1 river, or
- Collects in a local pond

This constraint keeps the Pass 1 network authoritative. If Pass 2 tributaries could cross Pass 1 boundaries, water in cell A might bypass A's river and drain to B's river instead—undermining the Pass 1 network's role as the primary drainage system.

**What Pass 2 cells do NOT do:**

- They don't cross into adjacent Pass 1 cells
- They don't recalculate or subdivide the Pass 1 river path
- They don't change where the Pass 1 river enters or exits the parent cell
- They don't affect the Pass 1 network in any way

The Pass 1 river's path through a cell is determined by Pass 1 neighbor relationships. If Pass 1 says "water enters from West, exits to East," that's fixed. Pass 2 adds tributaries that feed into that river, but the river itself is already placed.

**Example:**

A Pass 1 cell has input from the west (from its western Pass 1 neighbor) and output to the east (to its eastern Pass 1 neighbor). The Pass 1 river path through this cell is already determined by Pass 1—it enters from the west edge center and exits through the east edge center.

Pass 2 adds tributaries within this cell. The cell contains a 5x5 grid of Pass 2 cells (simplified for diagram clarity; actual subdivision is 7x7). Each Pass 2 cell has its own density:

```
Pass 1 cell with Pass 2 subcells (density values shown):

       ┌─────┬─────┬─────┬─────┬─────┐
       │ 0.8 │ 0.7 │ 0.6 │ 0.5 │ 0.4 │  Row 1
       ├─────┼─────┼─────┼─────┼─────┤
       │ 0.7 │ 0.5 │ 0.4 │ 0.3 │ 0.3 │  Row 2
       ├─────┼─────┼─────┼─────┼─────┤
input →● 0.6 │ 0.4 │ 0.3 │ 0.2 │ 0.2 ●→ output
       ├─────┼─────┼─────┼─────┼─────┤  (Pass 1 river, already fixed)
       │ 0.7 │ 0.6 │ 0.5 │ 0.3 │ 0.3 │  Row 4
       ├─────┼─────┼─────┼─────┼─────┤
       │ 0.9 │ 0.8 │ 0.7 │ 0.6 │ 0.5 │  Row 5
       └─────┴─────┴─────┴─────┴─────┘
         A     B     C     D     E
```

**The Pass 1 river is already placed.** It runs west-to-east through this cell. Pass 2 doesn't recalculate this path—it adds tributaries that feed into it.

**Pass 2 tributaries flow toward lower density:**

Each Pass 2 cell compares its density to its Pass 2 neighbors and flows toward the lowest one. Eventually, tributaries reach the Pass 1 river and merge into it:

```
       ┌─────┬─────┬─────┬─────┬─────┐
       │  ↓  │  ↓  │  ↓  │  ↓  │  ↓  │  Row 1 → Row 2
       ├─────┼─────┼─────┼─────┼─────┤
       │  ↓  │  ↓  │  ↓  │  ↓  │  ↓  │  Row 2 → Pass 1 river
       ├─────┼─────┼─────┼─────┼─────┤
input →●═══════════════════════════════●→ output (Pass 1 river)
       ├─────┼─────┼─────┼─────┼─────┤
       │  ↑  │     │  ↑  │  ↑  │  ↑  │  Row 4 → Pass 1 river
       ├─────┼─────┼─────┼─────┼─────┤
       │  ↑  │  →  │  ↑  │  ↑  │     │  Row 5 → Row 4
       └─────┴─────┴─────┴─────┴─────┘
```

- Rows 1-2 have tributaries flowing south toward the main river
- Rows 4-5 have tributaries flowing north toward the main river
- B4 failed its participation check—no tributary there (dry terrain)
- E5 is a local minimum that doesn't connect to the main river—it becomes a small pond

**Non-participating Pass 2 cells are just dry terrain.** They don't break the Pass 1 river—that river exists regardless. They just mean "no tributary forms in this spot."

**Elevation inheritance:**

Child cells inherit elevation context from their parent. The parent's distance-to-ocean determines the baseline elevation for all children. Children add finer elevation variation within that baseline—entrance-side subcells are slightly higher than exit-side subcells, creating a gentle slope across the parent cell. The overall descent follows the parent's position in the network.

**Width calculation:**

Tributaries derive width from their distance to terminus, same as main rivers. A tributary far from where it joins the main river is narrower than one close to the junction.
