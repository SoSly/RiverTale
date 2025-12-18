---
type: poc
validates: "[[River Terrain System]]"
assumption: "The Default River feature can carve visually correct river channels given CBN-style inputs"
status: success
---

## Goal

Verify that our channel carving algorithm produces correct terrain modifications when given the inputs that CBN would normally provide. This validates the core terrain modification logic in isolation—before integrating with CBN or the full feature-based architecture.

This PoC tests:
- Channel cross-section shape (rounded, configurable depth)
- Valley carving when terrain is higher than target elevation
- Embankment building when terrain is lower than target elevation
- **Variable elevation** along the river path (catenary curves)
- **Bezier curved paths** with directional control
- **Formula-derived dimensions** from accumulation and slope
- Meandering behavior

This PoC does NOT test worldgen integration (surface rules, heightmaps during generation). Those concerns are covered by the Surface Rule Embankment Test PoC.

## Approach

Create a command that accepts CBN-equivalent parameters and carves a river segment into existing terrain:

```
/rivertale poc carve <entryX> <entryZ> at <entryDist> <direction> <exitX> <exitZ> at <exitDist> acc <accumulation>
```

### Input Parameters

These mirror what the real carving system would receive from CBN:

| Parameter | Type | Description |
|-----------|------|-------------|
| `entryX`, `entryZ` | int | Entry point world coordinates (where water flows in) |
| `entryDist` | int | Entry distance from ocean; derives elevation via `SEA_LEVEL + (dist × ELEVATION_PER_CELL)` |
| `direction` | string | Initial heading (north/south/east/west) before curving toward exit |
| `exitX`, `exitZ` | int | Exit point world coordinates (where water flows out) |
| `exitDist` | int | Exit distance from ocean (typically < entryDist since water flows downhill) |
| `accumulation` | int | Water accumulation factor; drives width via logarithmic formula |

### Derived Values

The command computes these from parameters (matching production logic):

**Elevation:**
- Entry Y = `SEA_LEVEL + (entryDist × ELEVATION_PER_CELL)`
- Exit Y = `SEA_LEVEL + (exitDist × ELEVATION_PER_CELL)`
- Intermediate elevations follow a catenary curve (natural droop)

**Dimensions:**
- Width = `MIN_WIDTH + WIDTH_PER_ACC_LOG × ln(1 + accumulation) × slopeModifier`
- Depth interpolates linearly from width: MIN_WIDTH→MIN_DEPTH, MAX_WIDTH→MAX_DEPTH
- Slope modifier reduces width/depth on steep terrain

**Bank slopes:**
- Embankment: `waterSurfaceY - (distance / BANK_SLOPE)` — slopes down from water level
- Valley: `waterSurfaceY + (distance / BANK_SLOPE)` — slopes up from water level
- Both use the same `BANK_SLOPE` constant for smooth transitions

### What the Command Does

1. **Pre-load chunks**: Calculate all chunks within search radius and force-load them (prevents heightmap returning -64 for unloaded chunks)
2. **Path calculation**: Generate Bezier curve from entry through control point (direction × distance) to exit, with noise-based meandering
3. **Elevation interpolation**: Calculate catenary curve from entry elevation to exit elevation
4. **For each position along the path**:
   - Calculate water surface Y at this point (catenary interpolation)
   - Carve channel cross-section (rounded parabolic profile) from riverbed to sky
   - Place riverbed stone at channel bottom
   - Valley carving: remove terrain above valley floor target
   - Embankment building: fill terrain below embankment top target
   - Clear trees/structures above embankments being built
5. **Report statistics**: Blocks removed, blocks placed, elapsed time, chunks loaded, ms/chunk

### Test Scenarios

**Elevation configurations:**

| Scenario | Entry Dist | Exit Dist | Effect |
|----------|------------|-----------|--------|
| Flat river | 4 | 4 | No elevation change, maximum width/depth |
| Gentle descent | 6 | 4 | 8-block drop, moderate slope reduction |
| Steep descent | 15 | 5 | 40-block drop, significant narrowing |

**Accumulation variations:**

| Accumulation | Expected Width | Use Case |
|--------------|----------------|----------|
| 1 | ~4 blocks | Headwater stream |
| 5 | ~10-15 blocks | Regional tributary |
| 15 | ~25-30 blocks | Major river |
| 50+ | ~40 blocks (max) | Continental river |

**Terrain relationships:**

| Scenario | Setup | Expected Result |
|----------|-------|-----------------|
| At-grade | River elevation ≈ terrain | Clean channel, minimal embankment/valley |
| Elevated river | River elevation > terrain | Embankments built up, slopes down from water |
| Depressed river | River elevation < terrain | Valley carved, slopes up from water |
| Transitional | River descends through varied terrain | Embankment at start, valley at end |

## Results

### Test Runs

**Test 1: Short flat river (baseline)**
```
Command: /rivertale poc carve 0 0 at 4 north 10 10 at 4 acc 1
Entry: (0, 0) Y=79 → Exit: (10, 10) Y=79
Width: 16 blocks, Depth: 8 blocks, Slope: 0.0000
Blocks removed: 1,248,012
Blocks placed: 9,840
Elapsed: 418ms across 100 chunks (4.2ms/chunk)
```

**Test 2: Long steep descent, low accumulation**
```
Command: /rivertale poc carve 0 0 at 15 north 200 -200 at 5 acc 1
Entry: (0, 0) Y=123 → Exit: (200, -200) Y=83
Width: 4 blocks, Depth: 3 blocks, Slope: 0.1414
Blocks removed: 1,366,444
Blocks placed: 46,438,115
Elapsed: 12,365ms across 293 chunks (42.2ms/chunk)
```

**Test 3: Long steep descent, medium accumulation**
```
Command: /rivertale poc carve 300 0 at 15 north 500 -200 at 5 acc 5
Entry: (300, 0) Y=123 → Exit: (500, -200) Y=83
Width: 10 blocks, Depth: 3 blocks, Slope: 0.1414
Blocks removed: 8,463,557
Blocks placed: 39,812,834
Elapsed: 11,371ms across 321 chunks (35.4ms/chunk)
```

**Test 4: Long gentle descent, medium accumulation**
```
Command: /rivertale poc carve 600 0 at 6 north 800 -200 at 5 acc 5
Entry: (600, 0) Y=87 → Exit: (800, -200) Y=83
Width: 29 blocks, Depth: 10 blocks, Slope: 0.0141
Blocks removed: 71,470,160
Blocks placed: 1,194,934
Elapsed: 8,641ms across 375 chunks (23.0ms/chunk)
```

### Performance Analysis

| Metric | Observation |
|--------|-------------|
| **Embankment dominates** | When river elevation >> terrain, embankment places tens of millions of blocks |
| **Valley dominates** | When river elevation ≈ terrain, valley carving removes tens of millions of blocks |
| **Slope impact** | Steep slopes (0.14) reduce width by ~70%, depth by ~60% |
| **Per-chunk cost** | 4-42ms/chunk depending on work volume |
| **Chunk loading** | Excluded from timing; prevents heightmap failures on unloaded terrain |

### Visual Quality Assessment

**Rating: Good → Excellent**

- Channels have natural rounded cross-sections (parabolic depth profile)
- Bezier curves create realistic river bends, not just noise wobble
- Catenary elevation produces natural-looking descent
- Embankments slope smoothly away from elevated rivers
- Valley walls slope smoothly up from depressed rivers
- No visible discontinuities between embankment and valley zones

### Technical Correctness Assessment

**Rating: Success**

- Stone placed correctly for embankments and riverbed
- Channel carving clears to sky (removes trees, structures, everything)
- Riverbed survives carving (placed before removal pass)
- Bank slopes use unified constant for smooth transitions
- Embankment clearing only affects areas where embankment is actually built (not natural terrain)
- Heightmap queries use MOTION_BLOCKING_NO_LEAVES (ignores tree canopy)

### Implementation Notes

Issues discovered and fixed during development:

1. **Chunk loading required**: Heightmap queries on unloaded chunks return -64. Pre-load all chunks within search radius before carving.

2. **Heightmap type choice**: Use `MOTION_BLOCKING_NO_LEAVES` for post-generation terrain queries (this PoC). This ignores tree canopy and returns actual ground level. During actual worldgen, use `WORLD_SURFACE_WG` instead—it's designed for that context and trees won't exist yet.

3. **Embankment clearing bug**: `getBlocksToClear()` was carving depressions into natural terrain where the embankment slope formula dipped below ground level. Fixed by only clearing above embankments we're actually building.

4. **Operation order**: Place blocks first (riverbed, embankment), then remove blocks (channel, valley). This ensures riverbed survives and embankment doesn't fill carved areas.

5. **Unified bank slope**: EmbankmentBuilder and ValleyCarver must use the same slope constant to prevent discontinuities at the transition.

## Configuration Reference

Key constants in `CarveConfig.java`:

| Constant | Default | Effect |
|----------|---------|--------|
| `TERRAIN_HEIGHTMAP` | MOTION_BLOCKING_NO_LEAVES | Heightmap type for terrain queries |
| `SEA_LEVEL` | 63 | Base elevation at ocean |
| `ELEVATION_PER_CELL` | 4 | Y increase per cell of distance |
| `MIN_WIDTH` / `MAX_WIDTH` | 2 / 40 | River width bounds |
| `MIN_CHANNEL_DEPTH` / `MAX_CHANNEL_DEPTH` | 3 / 16 | Channel depth bounds |
| `WIDTH_PER_ACC_LOG` | 20.0 | Width scaling per ln(accumulation) |
| `SLOPE_WIDTH_FACTOR` | 20.0 | How much slope narrows rivers |
| `SLOPE_DEPTH_FACTOR` | 15.0 | How much slope shallows rivers |
| `BANK_SLOPE` | 3 | Horizontal blocks per vertical block of bank |
| `MAX_SEARCH_EXTENSION` | 60 | Terrain modification radius beyond channel |
| `CATENARY_RATIO` | 0.2 | Elevation curve sag (0 = linear) |
| `BEZIER_CONTROL_RATIO` | 0.5 | Path curve toward initial direction |

## Conclusions

**The assumption is validated.** The channel carving algorithm produces visually correct river channels when given CBN-style inputs. All target features work correctly:

- **Variable elevation**: Catenary curves create natural river descent
- **Curved paths**: Bezier interpolation with direction control
- **Formula-derived dimensions**: Accumulation and slope drive width/depth
- **Valley carving**: Removes terrain above river level with gradual slopes
- **Embankment building**: Fills terrain below river level with stone
- **Rounded channels**: Parabolic depth profile, not rectangular

**Ready for integration.** The algorithm can be adapted for worldgen context, where:
- Heightmap queries are replaced by direct block access during generation
- Chunk pre-loading is unnecessary (chunks generate on demand)
- Block updates are batched or eliminated (no neighbor notifications needed)
- Per-chunk processing replaces whole-river processing

**Performance is acceptable** for a debug tool. Production worldgen will be faster (no chunk loading, no block update notifications, direct array access).

## Next Steps (for production)

1. Integration with actual worldgen pipeline (not command-based)
2. Water block placement (currently just carves, no water)
3. Biome-appropriate materials (not just stone)
4. Connection to river network/drainage basin system
5. Waterfall handling at steep elevation changes
