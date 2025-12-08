# Ideas

Incomplete concepts. These need design work before implementation.

## Continent Detection

Rivers flow from continental interiors to ocean edges. But what *is* a continent, and where is its center?

**Current implementation:**
- Flood-fill from a starting position, bounded by continentalness < -0.13
- Track highest `depth` value found during flood-fill as the "center"
- Fine-search phase refines using actual heightmap within 512 blocks of coarse result
- 16-block sample step, up to 100k samples, 16km max radius
- Continent ID computed from minimum visited region (for caching)

**Observed problems:**
- Inconsistent results: different starting positions or exploration patterns produce different centers
- Wrong centers: sometimes finds peaks that don't make sense for the terrain
- The `ContinentalnessCalculator` class exists but isn't actually used by the detector

**Unresolved:**
- Is "highest depth value" the right signal for continental center?
- Why does the fine-search use heightmap while coarse search uses depth function?
- Should continent detection be deterministic from coordinates alone, or is starting-position-dependence acceptable?
- Do we even need a single "center" or just a flow direction signal?

**Why this matters:**
If continent detection is inconsistent, rivers generated from different chunks might disagree about flow direction. This directly violates the Coherence pillar.

> **CBN Resolution:** Cell-based Networks makes continent detection obsolete. Each cell determines flow direction by comparing its density to immediate neighbors—no global continent center required. Flow is always toward lower density (toward ocean). The "center" concept is replaced by local gradient descent. See `docs/features/rivers/Cell-based Networks.md`.

## Generation Approach

Two approaches have been explored. Neither is fully validated.

### Noise-Based Rivers

Use ridge noise (1.0 - abs(simplex)) to create connected river patterns, modulated by continentalness.

**Known elements:**
- Ridge noise naturally creates branching, connected patterns
- Continentalness gradient provides flow direction (toward decreasing values)
- Chunk-local calculation possible - no global state required
- Width can derive from inverse continentalness (wider near ocean)

**Unresolved:**
- Does ridge noise reliably avoid ocean-to-ocean connections?
- How to prevent parallel rivers too close together?
- Flow direction from gradient may reverse in complex terrain
- Elevation variation along rivers unclear

### Explicit Geometry

Generate actual line segments radiating from continental centers toward oceans.

**Known elements:**
- Guarantees flow direction (lines drawn from high to low)
- Tributaries can branch recursively from main rivers
- Region-based caching (512x512 blocks) for performance
- Continental centers found via flood-fill detection

**Unresolved:**
- Requires pre-generation or caching - not purely chunk-local
- How to handle rivers crossing region boundaries?
- Continental detection has ~250m variance - acceptable?
- Performance cost of flood-fill detection on first chunk load

**Why this is unresolved:**
Both approaches have tradeoffs. Noise-based is simpler but may not guarantee the "no ocean-to-ocean" constraint. Explicit geometry guarantees direction but requires global state. Need to prototype both.

> **CBN Resolution:** Cell-based Networks is a third approach that combines the best of both. Like noise-based, each cell computes flow locally from density comparisons—no global state required. Like explicit geometry, flow direction is guaranteed (always toward lower density, terminating at ocean or lake). Ocean-to-ocean connections are impossible because ocean cells are terminuses with no outputs. See `docs/features/rivers/Cell-based Networks.md`.

## Tributary Networks

Small streams feeding into larger rivers, creating dendritic patterns.

**Known elements:**
- Tributaries branch at 20-45° angles from parent direction
- Length is 40-60% of parent river
- Recursive generation (max 3 levels suggested)
- Junctions should widen the downstream river

**Unresolved:**
- How do tributaries interact with chunk boundaries?
- Should tributaries be generated on-demand or pre-computed?
- How to ensure tributaries don't cross other rivers?
- What visual cues distinguish tributaries from main rivers?

> **CBN Resolution:** Tributaries emerge naturally from the cell grid structure. When multiple cells flow into the same downstream cell, their rivers merge—that's a tributary junction. Multi-pass generation creates hierarchical drainage: major rivers in early passes, tributaries in later passes. Tributaries can't cross other rivers because all flow follows the density gradient toward the same terminus. Chunk boundaries are handled by fixed edge centers where rivers cross cell boundaries. See `docs/features/rivers/Cell-based Networks.md`.

## Width Variation

Rivers widen as they approach the ocean.

**Known elements:**
- Narrow at source, wide at mouth
- Width indicates position in the river system (legibility)
- Could derive from continentalness (low = wide)
- Could derive from tributary accumulation (more feeders = wider)

**Unresolved:**
- Which derivation method produces better results?
- How to handle width at tributary junctions?
- Minimum and maximum width values?
- How does width affect terrain carving depth?

> **CBN Resolution:** Width derives from limited-depth upstream counting. A cell queries its immediate upstream neighbors (2-3 steps) to determine "few" vs "many" feeders. This is sufficient for width decisions without requiring global accumulation. Tributary junctions are where multiple upstream cells converge—width naturally increases there. Distance-to-ocean provides a fallback if upstream counting proves insufficient. See `docs/features/rivers/Cell-based Networks.md`.

## Frozen Rivers

Rivers in cold biomes should freeze but remain navigable.

**Known elements:**
- Ice at edges, open water in center
- Boats must never hit impassable ice
- Player enjoyment is the priority

**Unresolved:**
- How wide should the ice edges be?
- Should ice width vary with river width?
- How to handle the transition between frozen and unfrozen sections?
- Does this require custom ice placement logic or can surface rules handle it?

## Terrain Carving

Rivers need to carve valleys into the terrain.

**Known elements:**
- Three concentric zones suggested: valley (wide, gentle), banks (medium), bed (narrow, deep)
- Carving implemented via density function modification
- Water placed at calculated elevation using surface rules

**Unresolved:**
- How deep should river beds be carved?
- How to prevent floating terrain at river edges?
- How does carving interact with existing terrain features (caves, cliffs)?
- Should rivers carve through hills or go around them?

> **CBN Note:** Cell-based Networks determines river connectivity, not terrain carving. A separate river carving system consumes CBN data (input/output edge centers, distance-to-ocean, upstream count) and handles the actual terrain modification. See `docs/features/rivers/Cell-based Networks.md` for the data handoff.

## River Elevation

Rivers should exist at varying elevations based on position.

**Known elements:**
- Sea level (Y=63) at ocean
- Higher elevation inland
- Could derive from continentalness

**Unresolved:**
- How much elevation variation is desirable?
- Should water surface be flat or follow terrain?
- How to handle elevation changes without waterfalls (future scope)?
- What happens when river elevation conflicts with terrain?

> **CBN Resolution:** Elevation derives from distance-to-ocean measured in cells. Ocean cells have distance 0. Each upstream cell has distance = downstream neighbor's distance + 1. This count propagates via lazy recursion and is cached. Child cells in multi-pass generation inherit elevation context from their parent, with finer variation within the parent's baseline. See `docs/features/rivers/Cell-based Networks.md`.

## Meandering

Rivers should curve naturally, not run in straight lines.

**Known elements:**
- Noise-based lateral displacement can create S-curves
- Displacement should fade at river ends to maintain start/end positions
- Meandering shouldn't change flow direction

**Unresolved:**
- How much meandering looks natural vs chaotic?
- Should meander intensity vary along the river?
- How do meanders interact with tributary junctions?
- Can meandering be computed chunk-locally?

> **CBN Note:** Multi-pass subdivision provides natural meandering opportunity. Within a parent cell, the main river path follows density gradients through subcells—it doesn't have to be straight. Subcell density variation creates S-curves without explicit meander logic. The specific path rivers take within cells is left to the river feature generation system. See `docs/features/rivers/Cell-based Networks.md`.

## Waterfalls

**Status:** Future scope. Not for initial release.

Rivers descending cliffs could form vertical water features.

**Known elements:**
- Occur at sharp elevation transitions
- Erosion boundaries in Lithosphere indicate cliff locations

**Unresolved:**
- Everything. This needs full design treatment after core rivers work.

## Lakes

**Status:** Future scope. Not for initial release.

Rivers could originate from or drain into inland lakes.

**Known elements:**
- Lakes form in continental depressions
- Need to distinguish lakes from coastal bays
- Rivers can flow into lakes (endorheic basins)

**Unresolved:**
- Everything. This needs full design treatment after core rivers work.

> **CBN Resolution:** Endorheic basins emerge naturally from the cell algorithm. When a cell has no participating neighbor with lower density, and it's not ocean, it becomes a lake terminus. Water flows in but doesn't flow out. No special lake logic required—it falls out of the flow rules. See `docs/features/rivers/Cell-based Networks.md`.

## Deltas and Estuaries

**Status:** Future scope. Not for initial release.

Rivers could spread into multiple channels or wetlands near the coast.

**Known elements:**
- Occurs where rivers meet ocean
- Would enhance legibility (definitely near the sea)

**Unresolved:**
- Everything. This needs full design treatment after core rivers work.

## Lithosphere Integration

RiverTale targets Lithosphere terrain generation first.

**Known elements:**
- Lithosphere provides `depth` and `erosion` density functions
- Continentalness derivable: `depth * 0.7 + erosion * 0.3` (approximate)
- Vanilla river suppression already implemented via datapack
- Custom density functions can be injected for carving

**Unresolved:**
- Is the continentalness formula stable across Lithosphere versions?
- How to access density functions during biome source override?
- What's the performance impact of sampling density functions per-block?
- How to handle Lithosphere updates that change terrain generation?

> **CBN Note:** Cell-based Networks samples density via a subgrid averaged per cell, not per-block. This amortizes the cost. The specific density function source (Lithosphere's depth, a continentalness derivative, etc.) is an integration detail. CBN only requires that the density function be deterministic from world seed and coordinates. See `docs/features/rivers/Cell-based Networks.md`.

## Vanilla Compatibility

**Status:** Future scope. Lithosphere first.

Eventually RiverTale should work with vanilla terrain.

**Known elements:**
- Vanilla has its own continentalness parameter
- River suppression approach should transfer

**Unresolved:**
- Everything. Defer until Lithosphere integration is solid.
