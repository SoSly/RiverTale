Rivers are the arteries of a landscape. They carry water from highlands to sea, shape the terrain they flow through, and serve as natural highways for travel and trade. A river tells you where you are: upstream means highlands, downstream means ocean.

## Vanilla Minecraft

Vanilla rivers are decorative canals. They spawn based on noise patterns with no relationship to terrain. They connect oceans to oceans. They reverse direction mid-stream. They cut through mountains at the same elevation they cross plains.

A player following a vanilla river learns nothing about the world. The river doesn't lead anywhere meaningful. It's just wet terrain.

Vanilla incentivizes ignoring rivers entirely. They provide water for farms and occasionally block travel. They don't mean anything.

## The RiverTale Version

### The Fantasy

You're exploring unfamiliar terrain, no map, no coordinates. You crest a hill and spot a river cutting through the valley below. Immediately you know things:

- The ocean is *that* direction (downstream)
- High ground is *that* direction (upstream)
- This is a major drainage (the river is wide), so you're probably mid-continent
- If you follow this downstream, you'll hit the coast eventually

You descend to the riverbank and start walking downstream. The river widens as smaller streams join from side valleys. The terrain flattens. After a few minutes, you smell salt water. You've reached the sea without ever being lost.

Later, you need to find high ground for a mountain base. You remember that narrow stream you passed earlier. You retrace your steps upstream, following the water toward its source. The terrain rises. The stream shrinks to a trickle emerging from rocky highlands. You've found your mountain.

Rivers are infrastructure the world provides for free.

### Rivers as Consequences

Rivers exist because the terrain demands them. They don't appear randomly—they emerge from the shape of the land.

**What players see:**

- Rivers originate in highlands and flow toward the ocean
- Valleys form around rivers, not the other way around
- Tributaries join from side valleys where smaller drainages meet
- Endorheic basins collect water in natural bowls where terrain slopes inward

**What players experience by region:**

| Region | What You Find |
|--------|---------------|
| Continental interior | Narrow streams emerging from high ground, highland terrain |
| Mid-continent | Medium rivers with visible tributaries, valleys carved into terrain |
| Near the coast | Wide rivers at low elevation, multiple tributaries joining near the mouth |
| Endorheic basins | Rivers flowing into inland lakes, terrain sloping inward from all directions |

If someone asks "why is there a river here?", the answer is visible in the landscape.

### Rivers as Legible

Rivers encode geographic information. A player can read the landscape by reading the river.

**What rivers tell you:**

| Question | How Rivers Answer |
|----------|-------------------|
| Which way is the ocean? | Downstream. Always. |
| Am I near the coast or inland? | Wide rivers = coastal. Narrow streams = inland. |
| Where is high ground? | Follow rivers upstream to their sources. |
| Is this a continent or an island? | Large river networks = continent. No rivers = small island. |

**What river properties mean:**

- **Width** indicates position in the network. Narrow = near source. Wide = far downstream.
- **Elevation** indicates distance from ocean. High = inland. Sea level = coastal.
- **Direction** indicates slope. Downstream always points toward lower elevation.

A player can use rivers to navigate without a map. This is the core fantasy.

### Rivers as Coherent

Rivers follow consistent rules everywhere. A river segment generated today connects correctly to a river segment generated next week in an adjacent chunk.

**What players can rely on:**

- Following a river downstream always leads to ocean (or an endorheic lake)
- A river never reverses direction
- The same seed produces identical rivers, regardless of exploration order
- Rivers don't break at chunk boundaries

**How this changes player decisions:**

| Situation | Without RiverTale | With RiverTale |
|-----------|-------------------|----------------|
| Lost, need to find ocean | Wander randomly, check coordinates, or die and respawn | Find any river, walk downstream |
| Looking for high ground | Wander toward "mountainy-looking" terrain | Follow rivers upstream to their sources |
| Establishing a base | Location is arbitrary | River junctions are natural crossroads; coastal river mouths are harbors |
| Long-distance travel | Overland trudge or Nether highways | Rivers are highways; boat downstream is fast |
| Finding inland resources | Random exploration | Tributaries guide you into continental interior |

Players can trust rivers. That trust enables new gameplay strategies.

## Generation Requirements

For this fantasy to work, the generation system must solve several problems:

### Flow Direction

Every point along a river must agree on which way is downstream. This sounds trivial but isn't—Minecraft generates chunks independently, in whatever order the player explores. A river segment generated today must connect correctly to a river segment generated next week in an adjacent chunk.

The system needs a way to determine flow direction that produces identical answers regardless of when or in what order chunks are generated.

### Width Accumulation

Rivers widen as tributaries join. But when generating a chunk, how do you know how many tributaries are upstream? Those chunks might not exist yet.

The system needs a way to derive width from something that can be computed locally, or propagated efficiently from already-generated terrain.

### Elevation Consistency

Rivers descend toward the ocean. A river's elevation at any point should reflect its position in the network—high at the source, sea level at the mouth.

The system needs a way to assign elevation that's consistent across the entire river, even when chunks generate out of order.

### Ocean Termination

Every river must reach the ocean (or an endorheic basin). But during generation, you might not know where the ocean is—it could be thousands of blocks away in ungenerated terrain.

The system needs a way to guarantee rivers terminate correctly without requiring the entire continent to be analyzed.

### Determinism

Two players with the same seed must see identical rivers. This rules out any approach that depends on exploration order, cached state from previous sessions (unless that state is itself deterministic), or randomness that isn't seed-derived.

### Solution Direction

The planned approach divides the world into large cells. Each cell determines its flow direction, upstream accumulation, and elevation by comparing its seed-derived density value to its neighbors. This makes all properties computable without requiring neighboring chunks to exist first.

A separate technical document specifies the cell-based flow algorithm in detail.

## Pillar Verification

Any solution to these requirements must satisfy the design pillars. Here's what each pillar means concretely for rivers:

### Rivers Are Consequences

River placement must derive from terrain properties, not arbitrary noise. A river exists at a location because the surrounding terrain funnels water there. If someone asks "why is there a river here?", the answer should be visible in the landscape.

### Rivers Are Legible

River properties must encode geographic facts:

- **Width** indicates position in the network. Narrow = near source. Wide = many tributaries joined.
- **Elevation** indicates distance from ocean. High = inland. Sea level = coastal.
- **Direction** indicates slope. Downstream always points toward lower elevation, toward the sea.

A player should be able to read these properties without any UI, just by looking.

### Rivers Are Coherent

River generation must be purely deterministic from world seed and coordinates. No dependence on exploration order. No state that varies between play sessions. Two players with the same seed must see byte-identical river networks.

## Balance Targets

These are player-experience goals that constrain implementation. Specific numbers belong in the technical specification; these are the "why" behind those numbers.

### River Density

Players should encounter rivers frequently enough to use them for navigation. A player exploring in any direction should find a stream or river within roughly double render distance. Rivers shouldn't be rare discoveries—they should be reliable infrastructure.

Climate should influence density. Wetter regions have more rivers. Arid regions have fewer, making the ones that exist more significant.

River density should be configurable. Some players want river-rich worlds; others want rivers to feel special.

### River Width

Width should vary along the river's length. Source streams are narrow. Rivers near the coast are wide enough to feel like real geographic features.

Width could derive from distance to ocean (which propagates back during generation), from density values (lower density = wider), or from upstream cell count if that information is available. The technical specification must determine which approach is feasible.

Width calculation should incorporate seeded randomness so that rivers have variety. Two rivers at similar positions in their networks shouldn't look identical.

There should be minimum and maximum bounds. Even the smallest stream should be visibly "a stream." Even the largest river shouldn't be so wide it feels like an ocean channel.

### Elevation Change

Elevation change along rivers should usually be subtle—noticeable over long distances but not jarring. A player walking upstream should gradually realize they're climbing.

Occasionally, elevation change can be dramatic. Terrain features like cliffs or highlands might create steep river sections. This variety makes the world more interesting, as long as the dramatic cases are exceptions rather than the norm.

The system should support the full range: subtle, noticeable, and dramatic, with subtle being the default and dramatic emerging from terrain conditions.

## Scope Boundaries

The Design Pillars warn against Complexity Theater and Perfection Over Performance. This section defines what rivers must do to satisfy the pillars, and what they don't need to do.

### Minimum Viable River

A river system satisfies the pillars if:

1. **Consequence**: Rivers flow from high density terrain toward low density terrain. The player can look at the surrounding landscape and understand why the river is here.

2. **Legibility**: A player can determine "downstream leads to ocean" by observation. Width or elevation gives some sense of position in the network.

3. **Coherence**: Rivers don't break at chunk boundaries. Same seed produces same rivers.

That's it. Everything else—realistic tributary angles, perfect width accumulation, smooth elevation gradients, climate-based density variation—is enhancement, not requirement.

### What We're Not Building

- **Hydrological simulation**: We don't model rainfall, evaporation, or seasonal flow. Rivers exist because terrain density says so, not because we simulated a water cycle.

- **Perfect accumulation**: If upstream cell count is expensive to propagate, width can derive from density or distance-to-ocean instead. The player experience is "rivers widen toward the coast," not "rivers widen by exactly the volume of their tributaries."

- **Dynamic rivers**: Rivers don't change after generation. No erosion, no course changes, no flooding. The river network is baked into terrain.

- **Every edge case**: If handling ocean monuments or witch huts gracefully requires significant complexity, rivers can simply avoid structure bounding boxes. Imperfect but functional beats perfect but slow.

### Performance Guardrails

If any of the following become true, the implementation has crossed into Perfection Over Performance:

- Generating a chunk requires calculating cells more than a few dozen chunks away.
- River data requires more than a few bytes per cell to cache
- A player exploring new terrain experiences noticeable lag from river generation
- The solution requires pre-generating large regions before gameplay begins

A player waiting on slow chunk loading isn't admiring the river system.

## Existing Worlds

RiverTale modifies terrain generation. There is no clean way to add it to an existing world.

Existing chunks retain their original terrain. New chunks generate with RiverTale rivers. At the boundary, rivers will appear to start or end abruptly. This is unavoidable without regenerating existing chunks, which would destroy player builds.

**RiverTale should be installed before world creation.** Adding it to an existing world will cause visible discontinuities at explored chunk boundaries.
