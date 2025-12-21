---
level: 3
parent: "[[Rivers Feature Concept]]"
status: review
---

Rivers use a cell-based networking system to determine flow direction, connectivity, and elevation across the world.

## Region Structure

The world divides into a grid of square regions. Each region has:

1. A **density value** representing how inland the location is
2. **Four cardinal neighbors** (north, south, east, west)
3. **Edge centers** where rivers enter and exit the region
4. A **participation flag** determining whether this region is part of the river network

Regions only consider cardinal neighbors (N/S/E/W) for flow—diagonal flow is not supported.

Density represents distance from ocean. High density = continental interior. Low density = coastal. Below-threshold density = ocean. Density differences below 0.01 are treated as equal to prevent floating-point noise from causing unexpected flow directions in flat terrain. When neighbors have equal density, a seeded random tiebreaker determines flow direction.

**Density sampling:**

A region's density is averaged across a 8×8 grid of cells, not taken from a single center point. This captures the region's overall terrain character rather than an arbitrary point that might be anomalous (a mountain peak, a local depression, etc.).

The same 8×8 cell grid is reused for river path refinement, so the density values sampled here are available for pathfinding within the region.

**Participation:**

Not every region contains a river. Each region uses a seeded random check to determine whether it participates in the river network. Non-participating regions are invisible to the system—they have no inputs, no outputs, and water neither flows through them nor around them.

Participating regions only consider other participating regions when comparing densities. If a region's only lower-density neighbor is non-participating, that region becomes a basin (local minimum from the river system's perspective).

Participation rate is a configurable percentage, independent of region size. This allows tuning river density separately from river detail:

| Region Size | Participation | Result |
|-------------|---------------|--------|
| Large | Any | Major rivers only, sparse network |
| Small | Low | Detailed river paths, sparse network |
| Small | High | Dense, highly branched river network |

## Flow Determination

Each region determines its flow by comparing its density to its participating neighbors. Non-participating neighbors are ignored entirely. This comparison is deterministic—same coordinates and world seed always produce the same result.

**Outputs:**

A region outputs water toward participating neighbors with lower density. The lowest-density participating neighbor becomes the primary exit. Water from upstream regions flows through this region and exits through that edge's center point.

**Secondary outputs are new river sources, not splits.**

If multiple participating neighbors have lower density, the region marks secondary outputs toward each additional neighbor. These secondary outputs represent *new rivers originating from this region*, not water splitting from the main flow.

Example: Region A has density 0.5. Its neighbors are North (0.3), South (0.4), East (0.6), West (0.7).

```
        North (0.3)
            ↑
            │ primary output
            │
West ──→  Region A  ←── East
(0.7)     (0.5)        (0.6)
            │
            │ secondary output (NEW SOURCE)
            ↓
        South (0.4)
```

- Primary output = North (lowest density neighbor)
- Secondary output = South (also lower than A, but not the lowest)
- Water from East and West flows through A and exits North
- A is ALSO a local high point relative to South, so A is a **source** for a separate river flowing South

The river flowing South didn't come from East or West—it *originates* at A. Region A sits on a drainage divide: water falling on A's north side flows north; water falling on A's south side flows south.

**Basins:**

If no participating neighbor has lower density and the region isn't ocean, the region is a local minimum. Water pools here. This is a basin—an endorheic terminus with no outlet.

**Inputs:**

A region receives water from participating neighbors with higher density. Each higher-density participating neighbor means an inflow point at that shared edge's center.

Multiple inflows each pathfind toward the exit. When paths intersect, they merge at that point and share the remaining route. If paths don't intersect until the exit, they merge there—two streams dumping into the same output edge is a valid confluence.

**Region type:**

Regions are classified based on their cell densities. Two thresholds apply:
- **Ocean threshold** (continents < -0.13) — saltwater ocean
- **Lake threshold** (depth < 0) — terrain at or below sea level (y=63)

Region types:

| RegionType | Description | Behavior |
|------------|-------------|----------|
| Body | Minecraft placed water here (ocean or lake) | Terminus, no participation |
| Shore | Adjacent to Body regions | Terminus, participates (river ends at water's edge) |
| Fluvial | River passes through | Normal flow rules apply |
| Divide | Flow originates here (local maximum) | River source, normal flow rules apply |
| Basin | Flow-based local minimum | Terminus, rivers collect here |
| Barren | No visible water system | Not participating in river network |

Region type is determined during region creation by checking the 8×8 cell densities against both thresholds. No additional sampling required.

**Ocean termination:**

Ocean regions are terminuses. They have no outputs. Rivers entering ocean regions end at the coastline.

**Coastal regions:**

A coastal region straddles the boundary between land and ocean. Some cells are above the ocean threshold (land), others are below (ocean).

Coastal regions participate in the river network—they contain land where rivers can flow. But they are also terminuses: water flows in and reaches the ocean within this region. The river's effective exit is wherever the path first touches an ocean cell, not the region's edge center.

Coastal regions cannot receive input from ocean-side edges. Rivers don't flow in from the sea.

During path refinement, ocean cells act as terminuses. When the pathfinding algorithm reaches an ocean cell, the path ends there. The river has reached the sea.

```
Example coastal region (ocean threshold = -0.13):

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
reaches an ocean cell—perhaps (3,4) or (2,4). The river mouth
forms at the actual coastline, not at an arbitrary edge center.
```

This region type provides a hook for future estuary and delta features. A coastal region carries the context those systems need: cell-level coastline geography, upstream accumulation, and the specific cells where river meets ocean.

## Edge Centers

Rivers cross region boundaries at edge centers only. The north edge center of region (0, 0) is always at the same world coordinates, regardless of which region generated first.

This constraint ensures adjacent regions always agree on connection points. Region A says "I exit through my south edge center." Region B says "I receive input from my north edge center." Those are the same point in world coordinates.

## River Path Within a Region

Knowing a region's inputs and outputs tells you WHERE water enters and exits, but not the PATH it takes in between. A region with west input and east output could have a straight river, an S-curve, or a path that dips south before curving back. The path matters for carving.

**Refinement step:**

After all regions have determined their flow direction, a refinement algorithm runs to determine the actual river course within each participating region. This uses a 8×8 cell grid to sample density and route the river toward lower-density areas while connecting the required entry and exit points.

The 8×8 grid provides 64 cells per region for detailed path refinement.

```
Region with input from WEST, output to EAST:

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

The refinement grid is the same 8×8 cell grid used for tributaries. This serves two purposes:
1. Determines the actual path the main river takes
2. Provides the grid that tributaries flow toward

**Constraint:** The path must connect the entry edge center to the exit edge center. The algorithm finds the lowest-density route between these fixed endpoints, not a free-form path.

**Output:** A sequence of cells that the river passes through. The carving system uses this sequence to place the actual river blocks.

## Distance to Ocean

Rivers descend toward the ocean. Elevation derives from distance-to-ocean measured in regions.

**Calculation:**

When a region determines its primary output, it recursively queries the downstream neighbor for that neighbor's distance to ocean. The region's distance is one plus the downstream distance. Ocean regions return distance zero.

```
Region F outputs to Region E.
Region E outputs to Region D.
Region D is ocean.

F asks E: "What's your distance?"
E asks D: "What's your distance?"
D responds: "Zero. I'm ocean."
E calculates: 0 + 1 = 1. Returns 1 to F.
F calculates: 1 + 1 = 2.
```

Distance calculation recurses downstream until reaching a terminus, then returns. This is fast—even a massive continent is only ~25 regions across.

## Upstream Accumulation

Regions track upstream feeders to inform width calculation. A region with many upstream feeders carries more water and should be wider.

When a region needs its upstream count, it queries its neighbors: "do you flow into me?" For each neighbor that does, the region recursively counts that neighbor's upstream feeders, up to a limited depth (a few steps). Beyond that depth, additional upstream complexity doesn't meaningfully affect local width.

This limited-depth counting avoids the ordering problem that would occur with permanent registration. A region's upstream count is computed on demand from its immediate neighborhood, not accumulated from exploration history.

The count distinguishes "few" from "many" upstream feeders, which is sufficient for width calculation. A region receiving flow from one upstream region is narrower than one receiving flow from several, even if those several regions share common ancestors further upstream.

Note that upstream count only includes regions that flow *to this region*. If region A has upstream feeders and flows to region C as its primary output, region B (receiving A as a secondary output) only counts A itself—not A's upstream feeders. This is correct because the river flowing from A to B is a *new source* originating at A. The water from A's upstream feeders flows to C, not to B. B's river started at A, so A is its only upstream region.

## Emergent Behaviors

**Tributaries:**

When multiple regions output to the same downstream region, their rivers merge at that region. No explicit tributary logic required—branching emerges from the grid structure.

Smaller regions = more boundaries = more merge points = more branching.

**Endorheic basins:**

When all neighbors have higher density, the region has no output. Water collects here naturally. The region becomes a basin terminus, analogous to an ocean terminus but inland.

**Determinism:**

Flow direction derives from density comparisons. Density derives from world seed. Therefore, flow is deterministic from seed alone.

The first region to query a downstream chain triggers the recursive calculation, but the results are identical regardless of who asked first. Region E's output direction depends on Region E's neighbors' densities, not on whether Region F or Region B triggered the query.

**Acyclic guarantee:**

The flow graph cannot contain cycles. Every region outputs toward lower density, and density comparisons are transitive. If A flows to B, then B's density is lower than A's. B cannot flow back to A because A has higher density. This property is fundamental to the algorithm—the recursive distance-to-ocean query terminates because it always moves toward lower density until reaching ocean or a basin terminus.

## River Feature Generation

Once a region knows its inputs, outputs, distance, and upstream count, it provides this data to the river feature generator. The system determines *connectivity*. Feature generation determines *appearance*.

**What regions provide:**

- Input edge centers (where water enters)
- Output edge center (where water exits)
- Distance to terminus (for elevation calculation)
- Upstream count (for width calculation)
- Whether this region is a source (no inputs, higher than all neighbors)
- Whether this region is a basin terminus (no outputs, not ocean)

**What regions don't determine:**

- The specific path rivers take within the region
- River width in blocks
- Terrain carving depth
- Water surface elevation in world coordinates
- Visual features like rapids, meanders, or waterfalls

Those concerns belong to separate systems that consume region data.

**Consumer interface:**

Cell-based Networks provides connectivity data. A separate river carving and decoration system is responsible for querying regions and placing actual blocks during chunk generation. How that system determines which regions to query, how it interpolates river paths within regions, and how it coordinates with Minecraft's chunk generation pipeline are outside this document's scope. The boundary is the data regions provide; the consumption of that data is another system's concern.

## Performance Characteristics

**Computation cost:**

Region data is computed on demand, not cached. This keeps the system simple and makes determinism errors obvious—if two queries for the same region produce different results, something is broken.

Computation is fast:
1. **Region computation is cheap.** Each region does density sampling and neighbor comparison. No pathfinding, no flood fill.
2. **Chain length is bounded.** Distance-to-terminus recurses downstream, but even a massive continent is only ~25 regions across.
3. **All inputs are deterministic.** Density derives from world seed. Same coordinates = same result, always.

**World borders:**

Minecraft worlds have a configurable border (default ±30 million blocks). Regions near or beyond the border have missing neighbors in some directions.

This is a non-issue for flow calculation. The density function works for any coordinates—even beyond the world border where no chunks will ever generate. When a region at the world edge needs to compare density to a neighbor "outside" the border, it samples that neighbor's density normally. If the outside region has lower density, water flows that direction (toward an ocean that will never be visited). If higher, water flows inward.

The region network doesn't care whether chunks exist. It only cares about density values, which are computable anywhere. Rivers near the world border may flow toward unreachable oceans, but they'll still flow correctly toward lower density. Players at the border won't see discontinuities—just rivers heading off toward distant water.
