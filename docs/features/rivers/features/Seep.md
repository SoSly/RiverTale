---
level: 5
parent: "[[Feature Classification]]"
status: draft
---

# Seep — Spike

This spike documents the classification logic for SEEP sources. SEEP represents slow groundwater emergence through soil in vegetated, stable terrain — meadows, forest floors, and other lush areas where water seeps up gradually.

## Scope

This spike covers:
- SEEP classification criteria
- Constants and thresholds
- Implementation of `classify()` method

This spike does NOT cover:
- `shape()` implementation (terrain carving)
- `fill()` implementation (water placement)
- `profile()` implementation (Y interpolation)
- Visualization or rendering

## Classification Criteria

A cell is classified as SEEP if ALL of the following are true:

1. **Vegetated** — Vegetation above threshold (lush terrain)
2. **Stable** — Erosion below threshold (not heavily weathered)
3. **No inflow** — No D8 neighbor has flow direction pointing at this cell

## Constants

| Constant | Value | Notes |
|----------|-------|-------|
| VEGETATION_THRESHOLD | 0.4 | Above = lush terrain |
| EROSION_THRESHOLD | 0.3 | Below = stable, non-eroded terrain |

## Algorithm

```
classify(cell: Cell, watershed: Watershed): boolean
    // Vegetation check
    if cell.averageVegetation() <= VEGETATION_THRESHOLD:
        return false

    // Erosion check — must be stable terrain
    if cell.averageErosion() >= EROSION_THRESHOLD:
        return false

    // No inflow check
    if cell.hasInflowingNeighbor():
        return false

    return true
```

## Dependencies

Requires from Spatial Infrastructure:
- `Cell.averageVegetation()` — averaged vegetation across cell samples
- `Cell.averageErosion()` — averaged erosion across cell samples

Requires from Feature Classification:
- `Cell.hasInflowingNeighbor()` — true if any D8 neighbor flows toward this cell (defined in [[Feature Classification Implementation]])

## Edge Cases

### Low Vegetation

**Cause:** Sparse vegetation (desert, tundra, bare rock).

**Response:** Fails vegetation check. Seeps need lush terrain.

### High Erosion

**Cause:** Heavily eroded terrain (badlands, canyons).

**Response:** Fails erosion check. Seeps need stable soil.

### Has Inflow

**Cause:** Cell meets vegetation and erosion criteria, but a neighbor flows here.

**Response:** Fails inflow check. Not a source.

### Overlap with SNOWMELT

**Cause:** Cold, high terrain that also has high vegetation.

**Response:** SNOWMELT is checked first in the enum (more specific). If it matches SNOWMELT, it won't reach SEEP check.

## Validation

| Test | Setup | Expected |
|------|-------|----------|
| Vegetated, stable, no inflow | veg=0.6, erosion=0.1, no inflow | SEEP |
| Low vegetation | veg=0.2, erosion=0.1, no inflow | Not SEEP |
| High erosion | veg=0.6, erosion=0.5, no inflow | Not SEEP |
| Has inflow | veg=0.6, erosion=0.1, neighbor flows in | Not SEEP |

## Future Work

Not covered by this spike:

- **shape()** — Terrain at seep sources (probably minimal change)
- **fill()** — Water placement (gentle emergence, maybe waterlogged blocks)
- **profile()** — Y interpolation (probably linear default)
- **Visualization** — Lush vegetation, wet soil textures
