---
type: poc
validates: "[[River Terrain System]]"
assumption: "The Default River feature can carve visually correct river channels given CBN-style inputs"
status: pending
---

## Goal

Verify that our channel carving algorithm produces correct terrain modifications when given the inputs that CBN would normally provide. This validates the core terrain modification logic in isolation—before integrating with CBN or the full feature-based architecture.

This PoC tests:
- Channel cross-section shape (rounded, configurable depth)
- Valley carving when terrain is higher than target elevation
- Embankment building when terrain is lower than target elevation
- Path interpolation between entry and exit points
- Meandering behavior

This PoC does NOT test worldgen integration (surface rules, heightmaps during generation). Those concerns are covered by the Surface Rule Embankment Test PoC.

## Approach

Create a command that accepts CBN-equivalent parameters and carves a river segment into existing terrain:

```
/rivertale poc carve <entryX> <entryZ> <exitX> <exitZ> <distanceToOcean> <width>
```

### Input Parameters

These mirror what the real carving system would receive from CBN:

| Parameter | Type | Maps To |
|-----------|------|---------|
| `entryX`, `entryZ` | int | Entry point world coordinates (where water flows in) |
| `exitX`, `exitZ` | int | Exit point world coordinates (where water flows out) |
| `distanceToOcean` | int | Cell distance from terminus; derives target elevation via `sea_level + (distance × elevationPerCell)` |
| `width` | int | River width in blocks (2-40 range per spec) |

### Derived Values

The command computes these from parameters (matching production logic):

- **Target elevation**: `63 + (distanceToOcean × 4)` (using default elevationPerCell=4)
- **Channel depth**: Scales with width, 3-8 blocks below water surface
- **Valley slope**: 30 blocks horizontal distance for walls
- **Embankment slope**: 40 blocks horizontal distance for fill

### What the Command Does

1. **Path calculation**: Interpolate a smooth path from entry to exit with noise-based meandering
2. **For each position along the path**:
   - Sample existing terrain height
   - If terrain > target elevation: carve valley (remove blocks)
   - If terrain < target elevation: build embankment (place stone)
   - Carve channel cross-section below target water surface
3. **Report statistics**: Blocks removed, blocks placed, elapsed time

The command modifies existing terrain directly. Since this runs in a fully-generated world, we're testing the algorithm's output, not worldgen pipeline behavior.

### Test Scenarios

Run the command in various terrain contexts:

**Terrain relationship to river:**

| Scenario | Setup | Expected Result |
|----------|-------|-----------------|
| Flat terrain at target elevation | Plains biome, `distanceToOcean=5` (Y≈83) | Clean channel carved, minimal valley/embankment work |
| Terrain above target | Mountain biome, `distanceToOcean=2` (Y≈71) in terrain at Y=100+ | Valley carved through mountain, walls slope up to existing terrain |
| Terrain below target | Ocean-adjacent plains, `distanceToOcean=10` (Y≈103) in terrain at Y=70 | Embankments built up from stone |
| Mixed terrain | River crosses from plains into hills | Smooth transition between embankment and valley sections |

**Width variations:**

| Width | Expected |
|-------|----------|
| 3 blocks | Narrow stream, minimal disturbance |
| 12 blocks | Regional river, moderate valley |
| 30 blocks | Major river, wide valley/embankments |

**Path configurations:**

| Config | Entry → Exit | Purpose |
|--------|--------------|---------|
| Straight | (0, 0) → (200, 0) | Baseline behavior |
| Diagonal | (0, 0) → (200, 200) | Angled carving |
| Long segment | (0, 0) → (500, 100) | Multi-chunk carving |

### What to Observe

**Visual inspection:**
- Channel has rounded cross-section (not rectangular)
- Valley walls slope gradually, not cliff faces
- Embankments slope gradually, not vertical walls
- Path meanders naturally (not perfectly straight even for straight entry→exit)
- No obvious chunk boundary artifacts for long rivers

**Block-level verification:**
- Stone used for embankments (the material surface rules expect)
- Channel depth appropriate for width (wider = deeper within 3-8 range)
- No floating blocks or unsupported terrain left behind

**Edge cases:**
- River crossing existing water (ocean, lake)
- River crossing caves (should we cap them? leave them? document behavior)
- Very steep terrain (60+ block elevation difference)

## Success Criteria

**Visual quality:**
- **Acceptable**: Rivers are recognizable channels; some rough edges or unnatural slopes
- **Good**: Rivers look intentional; valleys and embankments blend with terrain
- **Excellent**: Rivers look like they belong; could pass for intentional terrain

**Technical correctness:**
- **Success**: Correct materials placed, slopes calculated correctly, no floating blocks
- **Partial**: Minor issues (occasional floating block, slope discontinuities)
- **Failure**: Algorithm produces unusable terrain, blocks placed incorrectly

**Performance:**
- **Acceptable**: < 5 seconds for 500-block river
- **Good**: < 2 seconds
- **Excellent**: < 500ms

If carving produces unusable results, we need to revisit:
1. Channel cross-section algorithm
2. Valley/embankment slope calculations
3. Path interpolation and meandering approach

## Results

*Not yet run.*

## Conclusions

*Pending results.*