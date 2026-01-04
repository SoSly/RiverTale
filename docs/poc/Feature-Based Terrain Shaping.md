---
type: poc
validates: "[[Terrain Shaping Architecture]]"
assumption: "Feature handlers can express partial opinions (via MAX_VALUE for 'no opinion'), multiple handlers can be called per chunk, and their outputs can be blended into coherent terrain"
status: pending
---

## Goal

The River Channel Carving PoC proved that carving works in isolation—one river, one pass, whole-river iteration. But production terrain shaping must:

1. Process multiple chunks, each potentially influenced by different features
2. Blend between adjacent cells with different features (Run next to Confluence, etc.)
3. Handle overlapping influence zones (parallel rivers, embankment interactions)
4. Work with real watershed data from RegionCache/CellCache, not command-line test parameters

This PoC tests whether the feature-based architecture can produce coherent terrain when multiple features interact across a region.

**Design decisions that depend on this:**

- Whether each feature handler computes its surface independently vs. needs context from neighbors
- Whether blending happens inside handlers or in a coordination layer
- What data the coordination layer needs (just target surfaces? distance from feature center? priority?)
- Whether the current `carve()` → `applyCarve()` flow is sufficient or needs restructuring

## Approach

Create a command that applies terrain shaping to all chunks within a radius of the player, using real watershed data from RegionCache/CellCache.

### Command

```
/rivertale poc shape <radius>
```

Where `<radius>` is in blocks (XZ plane). The command processes all chunks whose centers fall within that radius.

### What the Command Does

**Phase 1: Gather data**

1. Get player position as center point
2. Calculate all chunk coordinates within radius
3. For each chunk, query CellCache/RegionCache:
   - Get the cell containing this chunk (cells are chunk-aligned, so each chunk belongs to exactly one cell)
   - Get the cell's classified feature, plus adjacent cells that might influence this chunk
   - Get the Region's Watershed for topology (upstream/downstream) and Y levels
   - Compute width from accumulation: **+2 blocks per tributary, +1 block per 4 cells traveled**

Note: To get adjacent cells for influence blending, use `CellPos.relative(Direction)` for the 8 neighbors. A radius-based cell query will need to be built if we need cells beyond immediate neighbors.

**Phase 2: Compute target surfaces**

For each chunk:

1. Identify all cells that could influence this chunk (the chunk's own cell + adjacent cells)
2. For each influencing cell, call its feature handler's `carve(cell, watershed, chunk)`
3. Each handler returns a 16x16 `int[][]` representing its opinion:
   - Real Y values = "I want this block at this height"
   - `Integer.MAX_VALUE` = "I have no opinion on this block"
4. **Coordination step**: Blend non-MAX_VALUE values from all handlers

**Note**: Feature handler `carve()` methods currently return null (stubs). This PoC will implement select carvers to test the architecture.

### The Hypothesis Being Tested

The core assumption is that this multi-handler approach works:
- Each handler computes a *partial* surface (only blocks it cares about)
- Multiple handlers can be called for the same chunk with different cells
- Their partial opinions can be blended into coherent terrain

This might be DOA. If handlers can't express partial opinions cleanly, or if blending produces artifacts, we need a different architecture. That's what this PoC determines.

### Blending Approach (Initial)

The coordination step is the core unknown. Initial approach to test:

- For each block, collect all non-MAX_VALUE values from influencing handlers
- Weight each value by distance from the influencing cell's river path
- Use weighted average (or weighted minimum—test both)

**Phase 3: Apply terrain**

For each chunk:

1. Apply the blended target surface using existing `applyCarve()` logic
2. Track statistics: blocks modified, features processed, overlaps encountered

**Phase 4: Report**

Output:

- Chunks processed
- Features encountered (by type)
- Overlap zones (blocks where multiple features contributed)
- Time elapsed
- Any errors or edge cases hit

### Implementation Notes

This PoC runs as a command on existing terrain (like the Carving PoC), not during actual worldgen. This allows rapid iteration without regenerating chunks.

The command should pre-load chunks before processing to ensure heightmap queries work.

Implement select feature carvers as needed to test specific scenarios. Carving logic can be adapted from the existing `org.sosly.rivertale.poc.carve.*` classes (RiverCarveExecutor, ChannelCarver, etc.). Unimplemented features will return null and be skipped.

### Test Scenarios

**Scenario 1: Single river through multiple chunks**

Stand near a river that crosses several chunks. Run with radius 200.

- Expected: Continuous river channel with no seams at chunk boundaries
- Watch for: Discontinuities where chunk edges meet

**Scenario 2: Confluence**

Teleport to a location where visualization shows a confluence. Run with radius 200.

- Expected: Two rivers merge smoothly into one wider channel
- Watch for: Weird terrain at the junction, competing embankments

**Scenario 3: Parallel rivers**

Find two rivers running roughly parallel within ~100 blocks. Run with radius 300.

- Expected: Each river carves its channel; terrain between them is coherent
- Watch for: Overlapping embankments, terrain "fighting" between the two rivers

**Scenario 4: River source**

Find a river source in highland terrain. Run with radius 200.

- Expected: Channel emerges naturally from high ground
- Watch for: Harsh transition where river starts, floating embankments

### Visualization

Add debug particles or a `/rivertale visualize implement` mode that shows:

- Feature boundaries (which cells own which chunks)
- Overlap zones (where multiple features contributed)
- Target surface before vs. after blending

## Success Criteria

### Single River (Scenario 1)

| Rating     | Observation                                                |
| ---------- | ---------------------------------------------------------- |
| Failure    | Visible seams at chunk boundaries, or river doesn't appear |
| Acceptable | River appears, minor discontinuities at chunk edges        |
| Good       | Continuous channel across chunks, smooth terrain           |
| Excellent  | Indistinguishable from the single-river Carving PoC        |

### Feature Interaction (Scenarios 2-4)

| Rating     | Observation                                                                        |
| ---------- | ---------------------------------------------------------------------------------- |
| Failure    | Terrain is broken (floating blocks, holes, nonsensical shapes)                     |
| Acceptable | Terrain is technically correct but visually rough at transitions                   |
| Good       | Transitions look intentional; you can see where features meet but it's not jarring |
| Excellent  | Features blend seamlessly; a player wouldn't notice the boundary                   |

### Performance

| Rating     | Time per chunk                                              |
| ---------- | ----------------------------------------------------------- |
| Failure    | >500ms/chunk (would tank worldgen)                          |
| Acceptable | 100-500ms/chunk (needs optimization but validates approach) |
| Good       | 20-100ms/chunk (comparable to existing Carving PoC)         |
| Excellent  | <20ms/chunk                                                 |

### Coordination Layer

This is the core question. Success means we learn which blending approach works:

| Outcome                 | What We Learned                                                                        |
| ----------------------- | -------------------------------------------------------------------------------------- |
| Weighted average works  | Simple approach is sufficient; document the formula                                    |
| Weighted minimum works  | "Lowest wins" is the right mental model                                                |
| Neither works           | Need a different approach (priority system? explicit boundary handling?)               |
| Depends on feature type | Different features need different blending; coordination layer needs feature awareness |

## Open Questions

These should be answered by running the PoC:

1. **What data does blending need?** Just target Y? Or also distance-from-center, feature type, priority?
2. **Should handlers know about neighbors?** Or is post-hoc blending sufficient?
3. **How wide is the "blend zone"?** 8 blocks? 16? Variable by feature type?
4. **What about water?** This PoC focuses on terrain shape. Water placement is a separate pass—does that still work after blending?
5. **Is 8-neighbor adjacency enough?** Or do we need a radius-based cell query for features with large influence zones?

## Results

_Not yet run._

## Conclusions

_Pending results._
