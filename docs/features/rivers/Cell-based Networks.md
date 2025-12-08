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

A cell's density is averaged across a subgrid of sample points, not taken from a single center point. This captures the cell's overall terrain character rather than an arbitrary point that might be anomalous (a mountain peak, a local depression, etc.).

Subgrid resolution should scale with cell size—larger cells need more samples to represent their terrain accurately. The exact resolution is a tuning parameter that depends on cell size.

**Participation:**

Not every cell contains a river. Each cell uses a seeded random check to determine whether it participates in the river network. Non-participating cells are invisible to the system—they have no inputs, no outputs, and water neither flows through them nor around them.

Participating cells only consider other participating cells when comparing densities. If a cell's only lower-density neighbor is non-participating, that cell becomes a lake (local minimum from the river system's perspective).

Participation rate is a configurable percentage, independent of cell size. This allows tuning river density separately from river detail:

| Cell Size | Participation | Result |
|-----------|---------------|--------|
| Large | Any | Major rivers only, sparse network |
| Small | Low | Detailed river paths, sparse network |
| Small | High | Dense, highly branched river network |

## Flow Determination

Each cell determines its flow by comparing its density to its participating neighbors. Non-participating neighbors are ignored entirely. This comparison happens once, on first access, and the result is cached permanently.

**Outputs:**

A cell outputs water toward participating neighbors with lower density. The lowest-density participating neighbor becomes the primary exit. Water leaves through that edge's center point.

If multiple participating neighbors have lower density, the cell marks secondary outputs toward each additional neighbor. Each output—primary or secondary—is a point where water leaves this cell. The cell decides what feature to place at each exit: a tributary junction, a lake outlet, a spring, or simply a continuation of flow. The distinction between primary and secondary is routing priority, not feature type.

If no participating neighbor has lower density and the cell isn't ocean, the cell is a local minimum. Water pools here. This is a lake.

**Inputs:**

A cell receives water from participating neighbors with higher density. Each higher-density participating neighbor means an inflow point at that shared edge's center.

Multiple inflows merge somewhere within the cell before reaching the exit.

**Ocean termination:**

Ocean cells (density below threshold) are terminuses. They have no outputs. Rivers entering ocean cells end at the coastline.

## Edge Centers

Rivers cross cell boundaries at edge centers only. The north edge center of cell (0, 0) is always at the same world coordinates, regardless of which cell generated first.

This constraint ensures adjacent cells always agree on connection points. Cell A says "I exit through my south edge center." Cell B says "I receive input from my north edge center." Those are the same point in world coordinates.

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

Note that upstream count only includes cells that flow *to this cell*. If cell A has multiple upstream feeders but flows to cell C as its primary output, cell B (receiving A as a secondary output) only counts A itself—not A's upstream feeders. The count reflects what actually flows into this cell, not what flows through a shared ancestor.

## Emergent Behaviors

**Tributaries:**

When multiple cells output to the same downstream cell, their rivers merge at that cell. No explicit tributary logic required—branching emerges from the grid structure.

Smaller cells = more boundaries = more merge points = more branching.

**Endorheic basins:**

When all neighbors have higher density, the cell has no output. Water collects here naturally. The cell becomes a lake terminus, analogous to an ocean terminus but inland.

**Determinism:**

Flow direction derives from density comparisons. Density derives from world seed. Therefore, flow is deterministic from seed alone.

The first cell to query a downstream chain triggers the recursive calculation, but the results are identical regardless of who asked first. Cell E's output direction depends on Cell E's neighbors' densities, not on whether Cell F or Cell B triggered the query.

**Acyclic guarantee:**

The flow graph cannot contain cycles. Every cell outputs toward lower density, and density comparisons are transitive. If A flows to B, then B's density is lower than A's. B cannot flow back to A because A has higher density. This property is fundamental to the algorithm—the recursive distance-to-ocean query terminates because it always moves toward lower density until reaching ocean or a lake terminus.

## River Feature Generation

Once a cell knows its inputs, outputs, distance, and upstream count, it provides this data to the river feature generator. The cell system determines *connectivity*. Feature generation determines *appearance*.

**What cells provide:**

- Input edge centers (where water enters)
- Output edge center (where water exits)
- Distance to ocean (for elevation calculation)
- Upstream count (for width calculation)
- Whether this cell is a source (no inputs, higher than all neighbors)
- Whether this cell is a lake terminus (no outputs, not ocean)

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
- Lake terminus flag

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

**Subsequent access:**

Cache lookup only. No recursion, no density sampling, no neighbor comparison.

**Parallel exploration:**

Multiple threads may query cells concurrently during chunk generation. When two threads query the same uncached cell simultaneously, one thread performs the calculation while others block waiting for the result. The cache serves as the synchronization point—once a cell is cached, all subsequent queries return immediately without blocking.

The recursive distance-to-ocean query may touch many cells in sequence, but each cell in the chain is computed and cached independently. Threads only contend on individual cells, not entire query chains.

**Memory footprint:**

A few bytes per cell. With 8192-block cells, even a world explored to 10 million blocks radius contains only ~1500 cells per axis, or ~2.25 million cells total. At 8 bytes per cell, that's ~18MB—trivial.

## Multi-Pass Generation

River networks can be generated in multiple passes at different scales. Each pass subdivides the previous pass's cells and adds detail within the constraints established by the parent.

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

**Pass hierarchy:**

Pass 1 establishes major drainage patterns. Each subsequent pass subdivides the previous pass's cells and adds finer detail—regional rivers, then tributaries and streams.

**Inherited constraints:**

Child cells don't calculate flow independently. They inherit hard constraints from their parent cell:

- The parent's input edge center(s) define where water enters the subdivided region
- The parent's output edge center defines where water must exit
- Child cells adjacent to these points must flow toward/from them, regardless of density

Child cells that aren't adjacent to parent entry/exit points use normal density comparison to determine their flow direction. No explicit reachability check is performed. Each child cell simply follows the density gradient toward its lowest neighbor. If that path eventually reaches the main river path or exit point, the cell participates in the network. If it dead-ends at a local minimum that isn't connected to the exit path, that cell becomes a pond or is excluded. Reachability falls out naturally from applying the flow rules—it doesn't require a separate validation pass.

**Example:**

Parent cell has input from the west, output to the east (edge center). This example uses a 5x5 subdivision for diagram clarity; the actual subdivision factor would typically be larger (e.g., 8x8). Density values determine the main river path and tributary connections.

```
Parent cell subdivided into 5x5 (density values shown):

       ┌─────┬─────┬─────┬─────┬─────┐
       │ 0.8 │ 0.7 │ 0.6 │ 0.5 │ 0.4 │  Row 1
       ├─────┼─────┼─────┼─────┼─────┤
       │ 0.7 │ 0.5 │ 0.4 │ 0.3 │ 0.3 │  Row 2
       ├─────┼─────┼─────┼─────┼─────┤
input →●  0.6│ 0.4 │ 0.3 │ 0.2 │ 0.2 ●→ output
       ├─────┼─────┼─────┼─────┼─────┤
       │ 0.7 │ 0.6 │ 0.5 │ 0.3 │ 0.3 │  Row 4
       ├─────┼─────┼─────┼─────┼─────┤
       │ 0.9 │ 0.8 │ 0.7 │ 0.6 │ 0.5 │  Row 5
       └─────┴─────┴─────┴─────┴─────┘
         A     B     C     D     E
```

**Step 1: Calculate the main river path**

The main river must connect the entrance (west edge, row 3) to the exit (east edge, row 3). Following density gradients through the subcells:

```
Main river path: A3 → B3 → C3 → D3 → E3

       ┌─────┬─────┬─────┬─────┬─────┐
       │     │     │     │     │     │
       ├─────┼─────┼─────┼─────┼─────┤
       │     │     │     │     │     │
       ├─────┼─────┼─────┼─────┼─────┤
input ●══A3════B3════C3════D3════E3═●→ output
       ├─────┼─────┼─────┼─────┼─────┤
       │     │     │     │     │     │
       ├─────┼─────┼─────┼─────┼─────┤
       │     │     │     │     │     │
       └─────┴─────┴─────┴─────┴─────┘
```

In this case the path is straight because row 3 happens to have the lowest densities. With different density values, the main path could meander—dipping south through B4 before curving back north to reach the exit.

**Step 2: Tributaries flow toward the main path**

Remaining participating subcells flow toward the main river path based on density:

```
       ┌─────┬─────┬─────┬─────┬─────┐
       │  ↓  │  ↓  │  ↓  │  ↓  │  ↓  │  Row 1 → Row 2
       ├─────┼─────┼─────┼─────┼─────┤
       │  ↓  │  ↓  │  ↓  │  ↓  │  ↓  │  Row 2 → Main path
       ├─────┼─────┼─────┼─────┼─────┤
input →●═════════════════════════════●→ output (main path)
       ├─────┼─────┼─────┼─────┼─────┤
       │  ↑  │     │  ↑  │  ↑  │  ↑  │  Row 4 → Main path
       ├─────┼─────┼─────┼─────┼─────┤
       │  ↑  │  →  │  ↑  │  ↑  │     │  Row 5 → Row 4
       └─────┴─────┴─────┴─────┴─────┘
```

Each tributary cell flows toward lower density, ultimately reaching the main path. B4 is excluded as a sample of a non-participating cell.  E5 is excluded as an example local minimum that can't connect.

**Step 3: Non-participating cells do nothing**

Any subcell that fails its participation check simply doesn't generate a river feature. It's dry terrain within the parent cell.

**Elevation inheritance:**

Child cells inherit elevation context from their parent. The parent's distance-to-ocean determines the baseline elevation for all children. Children add finer elevation variation within that baseline—entrance-side subcells are slightly higher than exit-side subcells, creating a gentle slope across the parent cell. The overall descent follows the parent's position in the network.

**Width calculation:**

Tributaries derive width from their distance to terminus, same as main rivers. A tributary far from where it joins the main river is narrower than one close to the junction.
