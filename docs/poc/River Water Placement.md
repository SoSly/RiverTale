---
type: poc
validates: "[[River Terrain System]]"
assumption: "Water source blocks can fill carved river channels and remain stable"
status: pending
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

Create a command that fills a carved channel with water:

```
/rivertale poc fill <startX> <startZ> <endX> <endZ> <waterElevation> <width>
```

### Input Parameters

| Parameter | Type | Purpose |
|-----------|------|---------|
| `startX`, `startZ` | int | Starting point of fill region |
| `endX`, `endZ` | int | Ending point of fill region |
| `waterElevation` | int | Y level for water surface |
| `width` | int | Width of the fill region (should match carved channel) |

### What the Command Does

1. **Scan the fill region**: For each column within `width` blocks of the path from start to end
2. **Place water**: At each column, place water source blocks from `waterElevation` down to the riverbed (first solid block)
3. **Report statistics**: Water blocks placed, columns processed, elapsed time

### Test Scenarios

**Prerequisites**: Each scenario requires a carved channel. Use the Carving PoC first, or manually create test channels.

**Basic fill:**

| Scenario | Setup | Expected Result |
|----------|-------|-----------------|
| Simple channel | Carved channel at Y=70, flat terrain | Water fills channel, level with banks |
| Deep channel | 8-block deep channel | Water fills to surface level, not to riverbed |
| Wide channel | 30-block wide river | Water fills entire width uniformly |

**Terrain interactions:**

| Scenario | Setup | Expected Result |
|----------|-------|-----------------|
| Embankment section | Channel carved into embankment (terrain below water level) | Water contained by embankment walls |
| Valley section | Channel carved into valley (terrain above water level) | Water fills channel floor only |
| Mixed | Channel transitions between embankment and valley | Consistent water level throughout |

**Edge cases:**

| Scenario | Setup | Expected Result |
|----------|-------|-----------------|
| Cave intersection | Channel carved above a cave | Water should NOT drain into cave (riverbed blocks the cave) |
| Open cave | Carved channel with exposed cave | Document behavior—may need riverbed sealing |
| Ocean connection | Channel ending at ocean level | Water should merge seamlessly with ocean |
| Multi-chunk | 200+ block channel spanning chunks | No water level discontinuities at chunk borders |

### What to Observe

**Water behavior:**
- Water surface is flat at target elevation
- No water flowing over banks (embankments contain it)
- No water draining out (riverbed is solid)
- Water updates complete (no flowing water animations that don't resolve)

**Visual inspection:**
- Water reaches all parts of the carved channel
- No dry spots within the channel bounds
- Water level consistent from start to end
- Smooth connection to any existing water bodies

**Technical verification:**
- Water blocks are source blocks, not flowing
- F3 shows correct water level at various points
- After waiting 30+ seconds, water hasn't changed (stable)

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

Alternatively, manually dig a test channel to isolate water placement from carving.

## Results

*Not yet run.*

## Conclusions

*Pending results.*