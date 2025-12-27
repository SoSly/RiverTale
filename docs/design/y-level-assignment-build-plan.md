---
level: 5
parent: "[[watershed-y-levels]]"
status: draft
---

# Build Plan: Y-Level Assignment for River Cells

## Summary

Add y-level tracking to river cells during watershed building. Rivers follow terrain height, with normalization when paths would go uphill.

---

## Key Decisions

| Decision | Value | Rationale |
|----------|-------|-----------|
| Default y-level | 63 | Sea level, safe fallback before assignment |
| Path ordering | Longest first | Main trunk establishes baseline, tributaries adapt |
| Ordering tiebreaker | Distance from (0,0) | Deterministic when lengths equal |
| minSlope | 1 block/cell (configurable) | Gentle visible flow, tunable per world |
| Interpolation | Linear | Simple, can revisit if it looks janky |
| Anchor search failure | Invalidate branch | Shouldn't happen with correct flow calc |

---

## Architecture Overview

```
Watershed Constructor
    │
    ├── Sort paths (longest first, tiebreaker distance)
    │
    ├── For each path:
    │   └── addPath() — existing diagonal crossing logic
    │
    ├── assignYLevels() ← NEW
    │   ├── Process paths in same sorted order
    │   ├── Query terrain heights
    │   ├── Detect uphill → walk upstream to anchor → interpolate
    │   └── Handle confluence (at/above = accept, below = normalize)
    │
    └── reclassify() — existing feature classification
```

---

## File Structure

| File | Responsibility |
|------|----------------|
| `cell/Cell.java` | Add `int y` field to record |
| `config/CommonConfig.java` | Add `int minSlope` config |
| `river/Path.java` | Add `length()` and `source()` methods |
| `river/Watershed.java` | Path sorting, `assignYLevels()`, normalization logic |

---

## Phased Build Order

### Phase 1: Add y-level to Cell

**Goal:** Cell record includes y-coordinate with default value.

**Files:** `Cell.java`

**Details:**
- Add `int y` as final field in Cell record
- Default y = 63
- Update all Cell construction sites to include y parameter
- Add `withY(int)` method to create copy with new y value

**Validation:**
- Compiles
- Existing tests pass (update test fixtures to include y=63)

---

### Phase 2: Add minSlope config

**Goal:** Configurable minimum slope between cells.

**Files:** `CommonConfig.java`

**Details:**
- Add `int minSlope` field with default value 1
- Expose via getter

**Validation:**
- Config loads with default value
- Value accessible via config getter

---

### Phase 3: Add Path helper methods

**Goal:** Path exposes length and source for sorting.

**Files:** `Path.java`

**Details:**
- `length()` returns cell count
- `source()` returns first cell position

**Validation:**
- Unit test: path with 5 cells returns length 5
- Unit test: source() returns first cell

---

### Phase 4: Sort paths in Watershed

**Goal:** Paths processed longest-first with deterministic tiebreaker.

**Files:** `Watershed.java`

**Details:**
- Sort paths before processing:
  - Primary: length descending
  - Secondary: distance from origin ascending
- Pass sorted list to assignYLevels

**Validation:**
- Given paths of length [3, 7, 5], processed in order [7, 5, 3]
- Given paths of equal length, closer to origin processed first

---

### Phase 5: Implement assignYLevels

**Goal:** Each river cell gets terrain-based y-level with normalization.

**Files:** `Watershed.java`, `CellCache.java`

**Details:**

For each path in sorted order:
1. Query terrain heights for all cells
2. Initialize assigned heights to terrain heights
3. Forward pass through path:
   - If next cell is in existing path (confluence):
     - If current >= existing + minSlope: accept, stop
     - If current < existing + minSlope: normalize upstream, stop
   - If current < next + minSlope (uphill):
     - Normalize upstream
4. Apply final y-levels to cells via cache

Normalization algorithm:
1. Walk upstream from problem cell
2. At each step, required height increases by minSlope
3. Stop when terrain height >= required (anchor found)
4. If source reached without anchor: throw error (flow bug)
5. Linear interpolate from anchor to problem cell

Requires `CellCache.put(Cell)` method to update cells.

**Validation:**
- Path on flat terrain: all cells get terrain y
- Path with uphill: upstream cells raised, interpolated
- Confluence below existing: upstream normalized to meet
- Confluence at/above existing: accepted without change

---

## Testing Milestones

| After Phase | Testable Behavior |
|-------------|-------------------|
| 1 | Cells have y-level, default 63 |
| 2 | Config returns minSlope value |
| 3 | Path.length() and source() work |
| 4 | Paths sorted by length desc, then distance |
| 5 | Y-levels assigned, normalization works |

---

## Future Work

- **Curve interpolation:** Replace linear with smoother curve if ramps look unnatural
- **Waterfall detection:** Preserve large drops as explicit features
- **Carving integration:** Use y-levels to actually carve terrain

---

## Resolved: Height Estimation from Depth

### Discovery

Through empirical testing (972,800 samples), we discovered that the `depth` noise value at Y=63 correlates **perfectly** with actual terrain height:

| Metric | Value |
|--------|-------|
| Correlation (depth → height) | 1.0000 |
| R² | 1.0000 |
| Mean Absolute Error | 0.31 blocks |
| Predictions within 1 block | 97.9% |
| Max Error | 15.5 blocks |

### The Formula

```
estimatedHeight = 70 + 144 * depth
```

This is implemented in `Sample.estimatedHeight()`.

### Why This Works

The `depth` value from the noise router at Y=63 already encodes terrain height information. Minecraft's density functions compute depth as a combination of the Y-gradient and terrain offset. At sea level (Y=63), the depth value directly reflects how far above or below sea level the surface will be.

### Implementation Status

- `Sample.estimatedHeight()`: Implemented
- Visualization at actual Y-level: Implemented (edges include fromY/toY)
- Uphill normalization: Future work (Phase 5 of original plan)