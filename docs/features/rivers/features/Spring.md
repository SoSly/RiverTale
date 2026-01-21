---
level: 5
parent: "[[Feature Classification]]"
status: draft
---

# Spring — Spike

This spike documents the classification logic for SPRING sources. SPRING represents groundwater emergence from rock formations and aquifers — hillsides, valley walls, and other terrain where water wells up from deeper ground.

## Scope

This spike covers:
- SPRING classification criteria
- Constants and thresholds
- Implementation of `classify()` method

This spike does NOT cover:
- `shape()` implementation (terrain carving)
- `fill()` implementation (water placement)
- `profile()` implementation (Y interpolation)
- Visualization or rendering

## Classification Criteria

A cell is classified as SPRING if ALL of the following are true:

1. **Deep terrain** — Depth above threshold (thicker terrain, more groundwater capacity)
2. **Not ridgeline** — Ridges below threshold (springs emerge from slopes, not peaks)
3. **No inflow** — No D8 neighbor has flow direction pointing at this cell

## Constants

| Constant | Value | Notes |
|----------|-------|-------|
| DEPTH_THRESHOLD | 0.3 | Above = thick terrain with groundwater capacity |
| RIDGES_THRESHOLD | 0.5 | Below = not on a ridgeline |

## Algorithm

```
classify(cell: Cell, watershed: Watershed): boolean
    // Depth check
    if cell.averageDepth() <= DEPTH_THRESHOLD:
        return false

    // No inflow check
    if cell.hasInflowingNeighbor():
        return false

    // Ridges check
    if cell.averageRidges() >= RIDGES_THRESHOLD:
        return false

    return true
```

## Dependencies

Requires from Spatial Infrastructure:
- `Cell.averageDepth()` — averaged depth across cell samples
- `Cell.averageRidges()` — averaged ridges (PV) across cell samples

Requires from Feature Classification:
- `Cell.hasInflowingNeighbor()` — true if any D8 neighbor flows toward this cell (defined in [[Feature Classification Implementation]])

## Edge Cases

### Shallow Terrain

**Cause:** Thin terrain layer (exposed bedrock, shallow soil).

**Response:** Fails depth check. Springs need deeper groundwater sources.

### On Ridgeline

**Cause:** Cell is on a ridge or peak.

**Response:** Fails ridges check. Springs emerge from slopes and valleys, not ridge tops.

### Has Inflow

**Cause:** Cell meets depth and ridges criteria, but a neighbor flows here.

**Response:** Fails inflow check. Not a source.

### Overlap with Other Sources

**Cause:** Cell might match multiple source criteria.

**Response:** Enum ordering determines priority. SNOWMELT, RESURGENCE, SEEP, CRATER are checked before SPRING. If a cell matches an earlier source type, it won't reach SPRING check.

## Validation

| Test | Setup | Expected |
|------|-------|----------|
| Deep, not ridge, no inflow | depth=0.5, ridges=0.3, no inflow | SPRING |
| Shallow terrain | depth=0.1, ridges=0.3, no inflow | Not SPRING |
| On ridgeline | depth=0.5, ridges=0.7, no inflow | Not SPRING |
| Has inflow | depth=0.5, ridges=0.3, neighbor flows in | Not SPRING |

## Future Work

Not covered by this spike:

- **shape()** — Terrain at spring sources (rocky outcrop, spring pool)
- **fill()** — Water placement (bubbling source blocks)
- **profile()** — Y interpolation (probably linear default)
- **Visualization** — Rocky textures, water particle effects
