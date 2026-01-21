---
level: 5
parent: "[[Feature Classification]]"
status: draft
---

# Snowmelt — Spike

This spike documents the classification logic for SNOWMELT sources. SNOWMELT is the primary source type — rivers starting from snowmelt have the longest paths and greatest elevation drop, forming the backbone of the river system.

## Scope

This spike covers:
- SNOWMELT classification criteria
- Constants and thresholds
- Implementation of `classify()` method

This spike does NOT cover:
- `shape()` implementation (terrain carving)
- `fill()` implementation (water placement)
- `profile()` implementation (Y interpolation)
- Visualization or rendering

## Classification Criteria

A cell is classified as SNOWMELT if ALL of the following are true:

1. **Cold enough** — Temperature below threshold (snow accumulates here)
2. **High enough** — Elevation above threshold (mountain/highland terrain)
3. **No inflow** — No D8 neighbor has flow direction pointing at this cell

The third criterion is what makes it a *source* rather than part of a river course. If water flows into a cell from upstream, that cell isn't where the river starts.

## Constants

| Constant | Value | Notes |
|----------|-------|-------|
| TEMPERATURE_THRESHOLD | 0.2 | At or below = cold enough for snow. Frozen ocean is 0.0, cold biomes are 0.0-0.2 |
| HEIGHT_THRESHOLD | 100 | Sea level is 63; mountains start around Y=100+ |

These values are starting points. Tuning may be needed based on how Minecraft's temperature distribution interacts with terrain height.

## Algorithm

```
classify(cell: Cell, watershed: Watershed): boolean
    // Height check
    if cell.averageEstimatedTerrainHeight() < HEIGHT_THRESHOLD:
        return false

    // No inflow check
    if cell.hasInflowingNeighbor():
        return false

    // Temperature check
    if cell.averageTemperature() >= TEMPERATURE_THRESHOLD:
        return false

    return true
```

## Dependencies

Requires from Spatial Infrastructure:
- `Cell.averageTemperature()` — averaged temperature across cell samples
- `Cell.averageEstimatedTerrainHeight()` — averaged terrain height across cell samples

Requires from Feature Classification:
- `Cell.hasInflowingNeighbor()` — true if any D8 neighbor flows toward this cell (defined in [[Feature Classification Implementation]])

## Edge Cases

### Cold but Low

**Cause:** Cold biome at low elevation (frozen river biome, cold ocean shore).

**Response:** Fails height check. These aren't mountain snowmelt sources.

### High but Warm

**Cause:** High terrain in warm biome (jungle hills, savanna plateau).

**Response:** Fails temperature check. No snow accumulation here.

### Cold, High, but Has Inflow

**Cause:** Cell meets temperature and height criteria, but a neighbor's flow points here.

**Response:** Fails inflow check. This cell is downstream of the true source.

### Multiple Adjacent Snowmelt Cells

**Cause:** Large cold, high plateau — many cells meet all criteria.

**Response:** All are classified as SNOWMELT. Each traces its own flowline. Flowlines merge at confluences downstream. This is correct — a large snowfield can have multiple drainage points.

### Cell on Region Boundary

**Cause:** Checking D8 neighbors crosses into adjacent region.

**Response:** `CellCache.getOrCompute()` handles cross-region lookups. No special handling needed.

## Validation

| Test | Setup | Expected |
|------|-------|----------|
| Cold, high, no inflow | temp=0.1, height=120, no neighbor flows in | SNOWMELT |
| Too warm | temp=0.5, height=120, no inflow | Not SNOWMELT |
| Too low | temp=0.1, height=80, no inflow | Not SNOWMELT |
| Has inflow | temp=0.1, height=120, north neighbor flows south | Not SNOWMELT |
| Boundary cell | Valid snowmelt at region edge | SNOWMELT (cross-region lookup works) |
| Plateau | 4 adjacent cells all meet criteria | All 4 are SNOWMELT |

## Future Work

Not covered by this spike:

- **shape()** — How terrain is carved at snowmelt sources (probably minimal — water emerges from existing terrain)
- **fill()** — How water is placed (source blocks at surface)
- **profile()** — Y interpolation (probably linear default is fine)
- **Visualization** — Particle effects, texture hints for snowmelt areas
