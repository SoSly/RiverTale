---
level: 5
parent: "[[Feature Classification]]"
status: draft
---

# Crater — Spike

This spike documents the classification logic for CRATER sources. CRATER provides river sources in warm highland terrain where SNOWMELT doesn't apply — volcanic crater lakes that overflow to feed rivers.

## Scope

This spike covers:
- CRATER classification criteria
- Constants and thresholds
- Implementation of `classify()` method

This spike does NOT cover:
- `shape()` implementation (crater terrain carving)
- `fill()` implementation (water placement)
- `profile()` implementation (Y interpolation)
- Visualization or rendering

## Classification Criteria

A cell is classified as CRATER if ALL of the following are true:

1. **High enough** — Elevation above threshold (mountain/highland terrain)
2. **Warm enough** — Temperature above threshold (too warm for snowmelt)
3. **No inflow** — No D8 neighbor has flow direction pointing at this cell
4. **Random chance** — Low probability roll passes (craters are rare)

## Constants

| Constant | Value | Notes |
|----------|-------|-------|
| HEIGHT_THRESHOLD | 100 | Same as SNOWMELT |
| WARM_THRESHOLD | 0.2 | At or above = too warm for snowmelt, crater territory |
| CRATER_CHANCE | 0.05 | 5% chance — keeps craters rare |

## Algorithm

```
classify(cell: Cell, watershed: Watershed): boolean
    // Height check
    if cell.averageEstimatedTerrainHeight() < HEIGHT_THRESHOLD:
        return false

    // Temperature check — must be warm (not snowmelt territory)
    if cell.averageTemperature() < WARM_THRESHOLD:
        return false

    // No inflow check
    if cell.hasInflowingNeighbor():
        return false

    // Random chance — use Minecraft's PositionalRandomFactory
    random = positionSeededRandom("rivertale:crater", cell.pos())
    return random.nextFloat() < CRATER_CHANCE
```

## Dependencies

Requires from Spatial Infrastructure:
- `Cell.averageTemperature()` — averaged temperature across cell samples
- `Cell.averageEstimatedTerrainHeight()` — averaged terrain height across cell samples

Requires from Minecraft:
- `PositionalRandomFactory` via `RandomState` — position-seeded deterministic randomness. Use `randomState.getOrCreateRandomFactory(resourceLocation).at(x, y, z)` to get a `RandomSource` for a specific position.

Requires from Feature Classification:
- `Cell.hasInflowingNeighbor()` — true if any D8 neighbor flows toward this cell (defined in [[Feature Classification Implementation]])

## Edge Cases

### High and Cold

**Cause:** High terrain in cold biome.

**Response:** Fails temperature check. This is SNOWMELT territory.

### High, Warm, but Has Inflow

**Cause:** Cell meets height and temperature criteria, but a neighbor flows here.

**Response:** Fails inflow check. Not a source.

### Random Chance Fails

**Cause:** Cell meets all criteria but random roll is above CRATER_CHANCE.

**Response:** Not classified as CRATER. May fall through to SPRING or remain NONE.

### Adjacent Crater Cells

**Cause:** Two adjacent cells both pass all checks including random chance.

**Response:** Both become CRATER sources. Rare due to low probability, but possible. Each traces its own flowline.

## Validation

| Test | Setup | Expected |
|------|-------|----------|
| High, warm, no inflow, lucky | height=120, temp=0.5, no inflow, random < 0.05 | CRATER |
| Too cold | height=120, temp=0.1, no inflow | Not CRATER |
| Too low | height=80, temp=0.5, no inflow | Not CRATER |
| Has inflow | height=120, temp=0.5, neighbor flows in | Not CRATER |
| Unlucky roll | height=120, temp=0.5, no inflow, random > 0.05 | Not CRATER |
| Determinism | Same cell, same seed, multiple checks | Same result every time |

## Future Work

Not covered by this spike:

- **shape()** — Crater bowl terrain carving (depression with raised rim)
- **fill()** — Water placement (lake filling the crater)
- **profile()** — Y interpolation at crater overflow point
- **Visualization** — Volcanic terrain textures, steam particles
