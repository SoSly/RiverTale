---
type: poc
validates: "[[River Terrain System]]"
assumption: "Water source blocks can fill carved river channels and remain stable"
status: success
---

## Goal

Verify that our water placement approach correctly fills carved river channels. This tests whether water source blocks placed at a target elevation will fill a pre-carved channel and remain stable without draining or flooding.

This PoC tests:
- Water fills the carved channel area at the correct elevation
- Water doesn't drain into caves or flow out of embankments
- Water stays stable after placement (no infinite flow issues)
- Multi-chunk rivers have consistent water levels

This PoC assumes a channel has already been carved (either by the Carving PoC or manually). It tests only the water placement step.

## Approach

Create a command that fills a carved channel with water, using identical parameters to the carve command:

```
/rivertale poc fill <entryX> <entryZ> at <entryDist> <direction> <exitX> <exitZ> at <exitDist> acc <accumulation>
```

### Input Parameters

These mirror the carve command exactly, ensuring the fill follows the same path:

| Parameter | Type | Description |
|-----------|------|-------------|
| `entryX`, `entryZ` | int | Entry point world coordinates (where water flows in) |
| `entryDist` | int | Entry distance from ocean; derives elevation via `SEA_LEVEL + (dist × ELEVATION_PER_CELL)` |
| `direction` | string | Initial heading (north/south/east/west) before curving toward exit |
| `exitX`, `exitZ` | int | Exit point world coordinates (where water flows out) |
| `exitDist` | int | Exit distance from ocean (typically < entryDist since water flows downhill) |
| `accumulation` | int | Water accumulation factor; drives width via logarithmic formula |

### What the Command Does

1. **Reconstruct the path**: Create a `RiverPathInterpolator` with identical parameters and seed as the carve command. Call `generatePath()` to get the exact same `PathPoint` list with Bezier curve, meander noise, and catenary elevations.
2. **Pre-load chunks**: Force-load all chunks within the fill radius (same as carve).
3. **For each PathPoint along the path**:
   - Get `targetElevation` (water surface Y at this point)
   - Get derived `width` from the interpolator
   - For each column within the channel width: place water source blocks from `targetElevation` down to the first solid block (riverbed)
4. **Report statistics**: Water blocks placed, path points processed, elapsed time

### Test Scenarios

**Prerequisites**: Each scenario requires a carved channel. Run the carve command first, then the fill command with identical parameters.

**Basic fill (reuse carving tests):**

| Scenario | Carve Command | Fill Command | Expected Result |
|----------|---------------|--------------|-----------------|
| Flat river | `/rivertale poc carve 0 0 at 4 north 10 10 at 4 acc 1` | `/rivertale poc fill 0 0 at 4 north 10 10 at 4 acc 1` | Water fills wide, flat channel uniformly |
| Steep descent | `/rivertale poc carve 0 0 at 15 north 200 -200 at 5 acc 1` | `/rivertale poc fill 0 0 at 15 north 200 -200 at 5 acc 1` | Water follows catenary elevation from Y=123 → Y=83 |
| Wide river | `/rivertale poc carve 600 0 at 6 north 800 -200 at 5 acc 5` | `/rivertale poc fill 600 0 at 6 north 800 -200 at 5 acc 5` | Water fills 29-block-wide channel |

**Terrain interactions:**

| Scenario | Setup | Expected Result |
|----------|-------|-----------------|
| Embankment section | Carve with high entry elevation over low terrain | Water contained by embankment walls |
| Valley section | Carve with elevation matching terrain | Water fills channel floor only |
| Mixed | Carve a long river that transitions terrain types | Water level follows catenary, no discontinuities |

**Edge cases:**

| Scenario | Setup | Expected Result |
|----------|-------|-----------------|
| Cave intersection | Carve over terrain with caves below | Water should NOT drain (riverbed seals caves) |
| Open cave | Carve channel where cave mouth is exposed | Document behavior—may need additional sealing |
| Ocean connection | Carve with exitDist=0 (sea level) | Water merges seamlessly with ocean |
| Multi-chunk | Any 200+ block river | No water level discontinuities at chunk borders |

### What to Observe

**Water behavior:**
- Water surface follows catenary curve from entry to exit elevation
- No water flowing over banks (embankments contain it)
- No water draining out (riverbed is solid)
- Water updates complete (no flowing water animations that don't resolve)

**Visual inspection:**
- Water reaches all parts of the carved channel (follows Bezier curve path)
- No dry spots within the channel bounds
- Water level descends smoothly along the river (catenary, not stepped)
- Smooth connection to any existing water bodies

**Technical verification:**
- Water blocks are source blocks, not flowing
- F3 shows correct water level at various points along the path
- After waiting 30+ seconds, water hasn't changed (stable)
- Water elevation matches `targetElevation` from PathPoint at each position

## Success Criteria

**Correctness:**
- **Success**: Water fills channel completely, stays stable, contained by terrain
- **Partial**: Water fills but has minor issues (some flowing blocks, small gaps)
- **Failure**: Water drains, floods surrounding area, or doesn't fill properly

**Stability:**
- **Success**: Water unchanged after 60 seconds
- **Failure**: Water continues flowing, draining, or spreading

**Performance:**
- **Acceptable**: < 3 seconds for 500-block river
- **Good**: < 1 second
- **Excellent**: < 200ms

If water placement fails:
1. Need to ensure riverbed is solid (seal caves)
2. May need to place water in specific order (downstream first?)
3. May need waterlogged blocks at edges instead of source blocks

## Dependency

This PoC should run AFTER the River Channel Carving PoC. A carved channel is prerequisite for meaningful water testing.

**Critical**: Use identical command parameters for carve and fill. The fill command reconstructs the exact same path using `RiverPathInterpolator` with the world seed—different parameters will produce water in the wrong location.

```
# These must match exactly:
/rivertale poc carve 0 0 at 6 north 200 -200 at 4 acc 5
/rivertale poc fill  0 0 at 6 north 200 -200 at 4 acc 5
```

## Results

**Test run** (674-block river, 30 blocks wide, 20 block elevation drop):

```
/rivertale poc carve 425 -69 at 5 east 331 -735 at 0 acc 9
/rivertale poc fill 425 -69 at 5 east 331 -735 at 0 acc 9
```

**Output:**
```
Filling river: (425,-69) → (331,-735) width=30 depth=9
Pre-loading 229 chunks...
Starting fill: (425, -69) Y=83 → (331, -735) Y=63, direction=EAST, acc=9
Calculated width=30, depth=9
Fill complete: placed 243,222 water blocks across 674 path points in 1230ms (5.4ms/chunk)
```

**Observations:**

| Criterion | Result |
|-----------|--------|
| Water fills channel | Yes |
| Water follows catenary elevation | Yes |
| Water stable after 60 seconds | Yes |
| No draining | Yes |
| Contained by embankments | Yes |
| Multi-chunk consistency | Yes |
| Performance | Good (1.2s for 674-block river) |

**Known issue:** Where the water surface drops from one Y-level to the next along the catenary, Minecraft water physics creates sharp horizontal "steps." Source blocks at the same Y-level fill in any gaps between them, resulting in squared-off transitions rather than smooth gradients. This is a fundamental Minecraft limitation—water surfaces can only exist at discrete Y levels.

**Potential mitigation:** Use flowing water blocks at elevation transitions instead of source blocks. Flowing water has a natural cascading appearance that may look more organic. Worth exploring post-PoC.

## Conclusions

**Assumption validated.** Water source blocks can fill carved river channels and remain stable.

The fill command successfully:
- Reconstructs the exact carved path using identical parameters
- Queries actual terrain (not theoretical geometry) for organic shorelines
- Places water from riverbed to target elevation at each column
- Handles multi-chunk rivers without discontinuities

**For production implementation:**
1. The terrain-query approach works well for shoreline edges
2. Elevation transitions need attention—consider flowing water or other techniques to soften the Y-level steps
3. Performance is acceptable but could be optimized by batching block updates or using chunk-level operations