---
level: 5
parent: "[[Feature Classification]]"
status: draft
---

# Run — Spike

This spike documents the classification logic for RUN features. RUN is the default river course — any cell that's part of a watershed and doesn't match a more specific feature type.

## Scope

This spike covers:
- RUN classification criteria
- Implementation of `classify()` method

This spike does NOT cover:
- `shape()` implementation (channel carving)
- `fill()` implementation (water placement)
- `profile()` implementation (Y interpolation)
- Visualization or rendering

## Classification Criteria

A cell is classified as RUN if ALL of the following are true:

1. **In a watershed** — Cell belongs to a watershed
2. **Nothing else matched** — RUN is last in the COURSE category

RUN is the fallback. If a cell is part of a river and isn't a source, terminus, junction, waterfall, cascade, or rapids... it's a run.

## Algorithm

```
classify(cell: Cell, watershed: Watershed): boolean
    if watershed == null:
        return false

    if not watershed.contains(cell.pos()):
        return false

    return true
```

That's it. RUN relies on enum ordering — it's checked last among course types, so anything that reaches this check and passes the watershed membership test becomes a RUN.

## Dependencies

Requires from Watershed Building:
- `Watershed.contains(CellPos)` — checks if cell is part of this watershed

## Edge Cases

### Not in Watershed

**Cause:** Cell has no watershed membership (wasn't part of any traced flowline).

**Response:** Returns false. Only watershed members can be RUN.

### Null Watershed (Early Classification)

**Cause:** `classify()` called during early phase with null watershed.

**Response:** Returns false. RUN only applies during reclassification when watershed context exists.

### Would Match Other Course Type

**Cause:** Cell has steep drop (should be WATERFALL) or other special characteristics.

**Response:** Enum ordering handles this. WATERFALL, CASCADE, RAPIDS are checked before RUN. If they match, RUN is never reached.

## Validation

| Test | Setup | Expected |
|------|-------|----------|
| In watershed, nothing special | Normal river cell in watershed | RUN |
| Null watershed | Early classification phase | Not RUN |
| Not in watershed | Cell outside any watershed | Not RUN |
| Steep drop | Large entryY - exitY | Should match WATERFALL/CASCADE first, not RUN |

## Future Work

Not covered by this spike:

- **shape()** — Standard river channel carving
- **fill()** — Flowing water placement with correct level/direction
- **profile()** — Linear Y interpolation (default behavior)
- **Visualization** — Standard river rendering
