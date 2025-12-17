The Cell-based Network (CBN) determines WHERE rivers flow—which cells connect, flow direction, distance to ocean. The River Terrain System determines HOW rivers appear in the world—terrain modification that creates river valleys and channels before Minecraft applies surface decoration.

## Key Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| River-authoritative terrain | Rivers dictate elevation; terrain is carved or filled to match | Guarantees rivers descend toward ocean regardless of local terrain noise. Alternative (terrain-authoritative) would cause rivers to pool or reverse in valleys. |
| Feature-based architecture | Subcells select features by type; features handle terrain modification | Extensible—new features (waterfalls, rapids) register without changing core logic. Alternative (monolithic carver) would require extensive conditionals. |
| Inject at buildSurface HEAD | Mixin runs before vanilla surface rules | Surface rules automatically apply biome materials (grass, sand) to our modifications. Alternative injection points would require manual surface handling. |
| Stone for embankments | Fill with `defaultBlock` (stone) | Surface rules only decorate stone. Using dirt or other blocks would leave embankments visually distinct from natural terrain. |
| Deterministic feature selection | Seeded noise picks features from eligible set | Same world seed = identical rivers. No exploration-order artifacts. |

## The Core Problem

Minecraft's terrain doesn't know about rivers. Density functions shape continents, mountains, and valleys based on noise—but rivers need to follow drainage networks that span thousands of blocks. A river 50 cells from the ocean needs to be higher than one 10 cells away, regardless of what the terrain happens to be doing.

This creates conflicts. A mountain range might sit between a river's source and the ocean. A depression might exist where the river needs to maintain elevation. The terrain and the river network disagree about what should exist at a given location.

**RiverTale resolves this with river-authoritative terrain**: rivers dictate their elevation based on network position, and terrain is modified to accommodate. If CBN says a river flows through a location at Y=95, and terrain there is Y=120, the terrain gets carved. If terrain is Y=70, embankments get built.

This is aggressive but consistent. Rivers win conflicts with terrain.

## Generation Timing

River terrain modification happens at a specific moment in Minecraft's chunk generation:

1. **NOISE** — Base terrain blocks placed (stone, water, air)
2. **[River terrain modification]** — We modify the terrain shape
3. **SURFACE** — Surface rules apply biome materials (grass, sand, etc.)
4. **CARVERS** — Caves carved
5. **FEATURES** — Vegetation, structures, water placement

This timing matters because **surface rules see our modifications.** When we carve a valley or build an embankment, the modified terrain exists before surface rules run. Vanilla surface rules examine terrain and apply appropriate materials—grass on dirt in plains, sand in deserts, gravel in mountains. Our embankments receive the same treatment automatically. We place stone; surface rules add the grass on top.

## Configuration

These parameters control terrain modification behavior. They affect newly generated terrain only.

| Parameter | Default | Range | Effect |
|-----------|---------|-------|--------|
| `elevationPerCell` | 4 | 1-10 | Blocks of elevation rise per cell inland from ocean |
| `minChannelDepth` | 3 | 1-5 | Minimum blocks carved below water surface |
| `maxChannelDepth` | 8 | 5-20 | Maximum blocks carved below water surface |
| `minWidth` | 2 | 2-5 | Narrowest possible river (source streams) |
| `maxWidth` | 40 | 10-80 | Widest possible river (continental rivers) |
| `embankmentSlope` | 40 | 20-100 | Horizontal distance over which embankments rise |
| `valleySlope` | 30 | 10-50 | Horizontal distance over which valley walls rise |
| `meanderScale` | 0.3 | 0.0-1.0 | Lateral displacement magnitude for river curves |

Channel depth scales with river width: wider rivers carve deeper channels within the min/max range.

## Cell Data Consumption

This system consumes cell data from the Cell-based Network. See `Cell-based Networks.md` "What cells provide" (lines 356-372) for the complete interface.

**Fields we use:**

| Field | Type | How We Use It |
|-------|------|---------------|
| `riverPath` | List of subcell coordinates | Determines which subcells contain river segments and in what order |
| `distanceToOcean` | int | Calculates target elevation: `sea_level + (distance × elevationPerCell)` |
| `classification` | enum (LAND/OCEAN/COASTAL) | COASTAL cells terminate rivers at the coastline subcell, not an edge |
| `primaryOutput` | FlowDirection | Determines exit edge for path interpolation |
| `isBasin` | boolean | Basin cells are terminuses—rivers end in a pool, not at ocean |

**Fields we don't directly use:**

- `density` — Cell-based Networking uses this for flow calculation; we use the resulting `riverPath`
- `secondaryOutputs` — Creates additional river sources, but each source is a separate path we process independently
- `upstreamCount` — Width calculation uses this, but that logic lives in a layer between CBN and terrain modification (not documented here)

**Cache availability:**

Cell data is computed and cached during chunk generation before our mixin runs. If a cell isn't cached (shouldn't happen in normal flow), the query triggers computation. This adds latency to the first chunk touching that cell but doesn't break correctness.

## Feature-Based Architecture

Rivers are not uniform channels. A river's appearance varies along its length: springs bubble up at sources, channels widen at confluences, waterfalls plunge down cliffs, deltas spread at ocean mouths. The terrain system handles this variation through a **feature-based architecture**.

Each subcell along a river path selects a **feature** that determines how terrain is modified in that subcell. Features are registered handlers that know how to carve their specific river element. The selection process uses subcell conditions and seeded noise to pick deterministically from eligible features.

### Feature Types

Features are categorized by where in the river they can appear. A feature declares its type, and the selection process only considers features matching the subcell's position:

| Type | Location | Conditions |
|------|----------|------------|
| **Source** | First subcell of a new river | No inlets—either a true headwater (cell with no inputs) or a secondary output (new river from a drainage divide) |
| **Mouth** | Last subcell before terminus | Subcell borders ocean or drains into an endorheic basin |
| **Confluence** | Merging point | Subcell has 2+ inlets from upstream |
| **Midstream** | Everything else | Standard river segments between source and mouth |

A subcell has exactly one type. The type determines which features are eligible for selection.

### Feature Selection

For each subcell in a river path:

1. **Determine type** — Check inlet count, terminus status, and source status
2. **Get eligible features** — Filter registered features by type
3. **Evaluate conditions** — Each feature declares conditions that affect its weight (elevation drop, biome, width, etc.)
4. **Select feature** — Use seeded noise to pick from eligible features based on weights

The selection is deterministic: same world seed, same subcell, same feature. Noise coordinates derive from subcell world position.

### Feature Interface

Every feature receives the same context and fulfills the same contract:

**Input (what features receive):**
- Subcell world bounds (block coordinates)
- Entry points (where water flows in—zero for sources, one for midstream, two+ for confluence)
- Exit point (where water flows out—none for mouths terminating at ocean/basin)
- Target elevation at entry and exit
- River width at this subcell
- Biome context
- Noise sampler for deterministic randomization

**Output (what features must do):**
- Modify terrain within subcell bounds (carve, build embankments)
- Define the water surface area and elevation for later water placement
- Report the actual exit point(s) for downstream subcells to connect

Features may read terrain outside their bounds for context (e.g., checking surrounding elevation) but should only modify terrain within their subcell.

### Required Features

Two features must exist for the system to function:

**Default River** — The standard midstream channel. Handles the vast majority of subcells. Carves a rounded channel, builds embankments where needed, produces smooth meandering paths.

**Confluence** — Handles subcells where multiple rivers meet. Must merge inlet paths into a single outlet path, typically widening the channel to accommodate combined flow.

Without these, rivers cannot be generated. All other features are optional extensions.

## Default River Feature

The Default River feature handles standard midstream subcells. It implements the baseline terrain modification that all rivers share.

### Channel Carving

The river channel is carved 3-8 blocks below the target water surface, creating a riverbed without excavating deep into terrain. This preserves underground features below rivers.

The channel cross-section is rounded, not rectangular. Width determines lateral extent—a 20-block wide river carves a 20-block wide channel with gently sloping sides.

### Valley Carving

When terrain is higher than the river's target elevation, blocks are removed to form a valley. Valley shape depends on depth difference:

- Shallow cuts (< 10 blocks): gentle banks that blend into surroundings
- Deep cuts (> 20 blocks): steeper valley walls

### Embankment Building

When terrain is lower than the river's target elevation, embankments raise the surrounding terrain to contain water at the correct elevation.

Embankments use gradual slopes (30-50 blocks wide) to look like natural terrain features rather than artificial levees. The fill material is stone—the same "default block" that vanilla terrain generation uses. This is important: surface rules only apply decoration to stone. By using stone for embankments, we get biome-appropriate surface materials (grass, sand, snow) applied automatically.

### Path Interpolation

The path between subcell entry and exit uses smooth curve interpolation to avoid angular river segments. A noise-based offset displaces the path laterally, creating natural S-curves without changing overall flow direction.

Meandering magnitude scales with river width. Wide rivers curve dramatically. Narrow streams wind tightly.

### What Gets Replaced

Terrain modification removes any block in its path—stone, dirt, existing features. Rivers claim their space unconditionally. This prevents rivers from being blocked by terrain that happened to generate in their path.

## Confluence Feature

The Confluence feature handles subcells where two or more rivers merge. It solves the geometric problem of routing multiple entry points to a single exit.

### Merge Point Calculation

When multiple inlets arrive (potentially from opposing directions), the merge point is calculated deterministically:

```
merge_x = weighted_average(inlet_x_positions, inlet_widths)
merge_z = weighted_average(inlet_z_positions, inlet_widths)

// Bias toward exit
bias = 0.3
merge_x = merge_x + bias × (exit_x - merge_x)
merge_z = merge_z + bias × (exit_z - merge_z)
```

Wider inlets have more influence on merge point position. The bias toward the exit prevents merge points from landing awkwardly far from the outflow.

The specific algorithm doesn't matter as much as its determinism—the same inputs must produce the same merge point every time.

### Multi-Inlet Handling

For each inlet:

1. Route a path from inlet entry point to the merge point
2. Carve a channel appropriate to that inlet's width
3. Apply the same valley/embankment logic as Default River

All inlet paths terminate at the merge point. From there, a single channel continues to the exit.

| Inlet Count | Merge Behavior |
|-------------|----------------|
| 2 inlets | Standard Y-junction |
| 3 inlets | Three paths converging to central point |

### Width Handling

Post-merge width follows the "max plus bonus" rule:

```
post_merge_width = max(inlet_widths) + width_bonus
```

The widest incoming river dominates. The bonus (typically 10-20% of the second-widest inlet) reflects additional flow without causing unrealistic growth.

### Channel Shape

The confluence carves a wider area than standard segments to accommodate the merging flows:

```
confluence_area = circle(merge_point, radius = post_merge_width × 1.5)
```

This creates a natural "pooling" effect where rivers meet before narrowing back to the exit channel. The transition from multiple channels to single channel should be gradual, not abrupt.

### Elevation at Confluence

All inlets must arrive at the same elevation—the confluence's target elevation. Both Pass 1 and Pass 2 rivers share elevation context (Pass 2 inherits baseline from parent Pass 1 cell), so steep drops from pass differences alone shouldn't occur.

For steep elevation differences between inlets from other factors (unusual terrain, basin edges), the higher inlet's final subcells use gradual descent to reach the confluence elevation. Future waterfall features will replace this with vertical drops where appropriate.

## River Elevation

Rivers need consistent elevation to convey flow direction. A player should look at a river and understand which way leads to the ocean.

### Elevation Formula

Each river segment calculates its target elevation from CBN data:

```
river_elevation = sea_level + (distance_to_ocean × elevation_per_cell)
```

Where:
- `sea_level` is Minecraft's water level (Y=63)
- `distance_to_ocean` comes from CBN's cached cell data
- `elevation_per_cell` is configurable (typically 3-5 blocks)

A river 20 cells from the ocean targets Y = 63 + (20 × 4) = 143. One 5 cells away targets Y = 83. Network position determines elevation, not local terrain.

### Elevation Continuity

Within a cell, elevation interpolates smoothly between entry and exit. A cell receiving water from 20 cells inland and passing it toward 19 cells inland has a gentle drop across its length. This creates the visual "downhill toward ocean" that makes rivers readable.

### Elevation Drop and Feature Selection

The elevation difference between subcell entry and exit influences feature selection. Steep drops weight toward Rapids or Waterfall features. Minimal drops weight toward Lake features. This connects terrain to river character without hard-coding specific behaviors.

## River Width

Width encodes network position. Narrow streams indicate proximity to a source. Wide rivers indicate accumulated flow from tributaries.

### Width Calculation

Width derives from upstream accumulation. At confluences:

```
resulting_width = max(upstream_widths) + small_bonus
```

The largest incoming river dominates, with a small addition for the merger. Rivers get wider downstream but not infinitely so.

### Width Range

| Category | Width | Character |
|----------|-------|-----------|
| Source streams | 2-4 blocks | Intimate, barely navigable |
| Minor tributaries | 5-10 blocks | Comfortable boat passage |
| Regional rivers | 11-20 blocks | Major waterways |
| Continental rivers | 21-40 blocks | Dominant landscape features |

Width influences channel depth—wider rivers carve deeper channels.

## Heightmap Updates

After modifying terrain, heightmaps must be recalculated. Surface rules use heightmaps to find where to apply surface materials. If we carve a valley but don't update the heightmap, surface rules would try to apply grass at the old (higher) elevation—placing it in empty air above the valley floor.

After all terrain modifications complete for a chunk, heightmaps are recalculated. Then surface rules run and see the correct terrain shape.

## Water Placement

Water placement happens during feature generation, after all terrain modification is complete and surface rules have run.

Each feature defines its water surface area and elevation. The water placement pass fills these areas with source blocks, allowing Minecraft's water mechanics to handle local flow.

## Riverbed Surface

The riverbed receives surface materials appropriate to context:

**Biome influence:**
- Warm/dry biomes: sand
- Cold/mountainous biomes: gravel
- Swamps: mud
- Default: gravel

**Depth influence:**
- Shallow sections: coarser material (gravel, cobble)
- Deep sections: finer material (sand, clay)

Patches of different materials break up monotony.

## Biome Assignment

The river area receives the `minecraft:river` biome. This enables river-specific mob spawning (drowned, fish) and appropriate ambient sounds.

The overlay applies to water and immediate banks, not the entire valley.

## Pass Integration

Pass 1 and Pass 2 rivers use the same terrain system with different parameters.

**Pass 1 (Major Rivers):**
- Larger cells (~4096 blocks)
- Wider base width
- Deeper channels
- More prominent valleys

**Pass 2 (Tributaries):**
- Smaller cells (~585 blocks)
- Narrower base width
- Shallower channels
- Gentler banks

When tributaries meet main rivers, they merge naturally. The Confluence feature handles the junction, and paths connect at shared points.

## Boundary Constraints

Rivers connect at cell boundaries via fixed edge centers. Entry and exit points are computed from world coordinates, not from either cell's internal state. Adjacent cells always agree on connection points.

Subcells at cell edges must ensure their paths connect precisely to the edge center points defined by CBN.

## Chunk Independence

Each chunk modifies its own terrain independently using deterministic path data:

1. CBN paths are computed from the world seed—identical results everywhere
2. Each chunk modifies its portion of the river
3. Elevation and width calculations use world coordinates, not chunk-relative values
4. Gradual embankment slopes blend naturally at chunk boundaries

There is no inter-chunk communication during terrain modification. Determinism guarantees that independently-modified sections align perfectly.

## Performance Characteristics

**Most chunks have no rivers.** With reasonable cell sizes and participation rates, roughly 2% of chunks contain river segments. The other 98% check CBN data, find no river, and skip terrain modification entirely.

**River chunks do moderate work.** A river crossing a chunk might modify 500-3000 blocks depending on width and embankment needs. This is small compared to vanilla terrain generation's per-chunk work.

**CBN queries are cached.** Cell data computes once and caches permanently. Queries during chunk generation are instant lookups.

**Heightmap recalculation is bounded.** Recalculating heightmaps scans columns top-to-bottom—limited by chunk size (256 columns), not river complexity.

**Feature selection is cheap.** Evaluating conditions and rolling weighted noise is trivial compared to terrain modification.

## Pillar Verification

### Rivers Are Consequences

The terrain system doesn't decide where rivers go—CBN does, based on continental density. Feature selection adds variety but doesn't override network structure. The "why" of river placement remains rooted in continental structure.

### Rivers Are Legible

Width indicates accumulation—narrow upstream, wide downstream. Elevation indicates position—higher inland, sea level at coast. Feature types reinforce position: sources look like origins, mouths look like endings. Even dramatic terrain modification preserves these semantic relationships.

### Rivers Are Coherent

Terrain modification uses cached CBN data and seeded noise for feature selection and meandering. The same world seed produces identical rivers with identical features. Chunk generation order doesn't matter—each chunk modifies based on deterministic path data.

## Scope Boundaries

### What This System Does

- Provides feature-based architecture for river terrain modification
- Selects features per subcell based on type and conditions
- Implements Default River and Confluence features
- Modifies terrain to create river channels and valleys
- Builds embankments where terrain is too low
- Updates heightmaps so surface rules work correctly
- Handles both Pass 1 and Pass 2 rivers
- Provides channel shapes for later water placement

### What This System Does NOT Do

- Generate CBN connectivity data (cell network's job)
- Place water blocks (feature generation's job)
- Apply surface materials (surface rules do this automatically)
- Implement advanced features like Rapids, Waterfalls, or Lakes (future registrations)

---

## Appendix: Future Features

These features are not required for initial implementation. They register into the feature-based architecture when implemented.

| Feature | Type | Selection Criteria |
|---------|------|-------------------|
| Spring | Source | Default source in temperate biomes |
| Glacial Melt | Source | Source in cold/mountain biomes |
| Source Lake | Source | Rare; small pond at river origin |
| Rapids | Midstream | Moderate elevation drop (6-10 blocks per subcell) |
| Waterfall | Midstream | Steep elevation drop (10+ blocks per subcell) |
| Midstream Lake | Midstream | Rare; minimal elevation drop, wide valley |
| Confluence Lake | Confluence | Rare; weighted higher with 3+ inlets |
| Estuary | Mouth | Coastal mouth in flat terrain |
| Delta | Mouth | Coastal mouth with sediment deposits |
| Bay | Mouth | Coastal mouth in hilly terrain |
