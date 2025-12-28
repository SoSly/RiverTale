---
type: poc
validates: "[[River Suppression]]"
assumption: "Vanilla rivers can be erased at the noise phase by filling water with stone and reassigning biomes"
status: pending
---

## Goal

The current river suppression approach uses density function overrides and a slow biome filter mixin. This is:

1. **Implementation-dependent** — Different JSON overrides needed for vanilla, Lithosphere, and any other terrain datapacks
2. **Slow** — The biome filter streams through the entire parameter list
3. **Brittle** — Any datapack that modifies density functions differently could break suppression

If we can erase rivers *after* they generate (at the end of noise phase), we get a terrain-agnostic solution. Whatever worldgen system produced the rivers, we just detect and fill them.

This PoC validates whether that approach produces acceptable terrain.

## Approach

### Setup

1. **Disable density function overrides** — Rename or delete:
   - `src/main/resources/data/minecraft/worldgen/density_function/overworld/ridges_folded.json`
   - `src/main/resources/data/lithosphere/worldgen/density_function/river_valleys.json`

2. **Disable biome filter mixin** — Comment out `MultiNoiseBiomeSourceMixin` in `rivertale.mixins.json`

3. **Create PoC mixin** — New file `RiverErasurePocMixin.java` in the mixin package, injecting at the same noise phase hook as production code

### Erasure Logic

At the end of noise phase, for each column in the chunk:

1. **Detect** — Is this column's biome `minecraft:river` or `minecraft:frozen_river`?
2. **Find water** — Identify water blocks in the column
3. **Fill** — Replace water with stone up to... what height?
   - Option A: Average height of neighboring non-river columns
   - Option B: Query depth function if it gives pre-river terrain height
   - Option C: Just fill to water surface level (Y=63) and see what happens
4. **Reassign biome** — Set biome to match nearest non-river neighbor

### Observation

1. Generate a new world (or explore ungenerated chunks)
2. Find areas where vanilla would have placed rivers
3. Observe:
   - Are rivers actually gone?
   - Does filled terrain look natural?
   - Are there visible artifacts at former river banks?
   - How does it interact with terrain features (hills, cliffs)?

## Success Criteria

### Detection
- **Pass**: River biomes are correctly identified in >99% of cases
- **Fail**: Significant false negatives (rivers not detected) or false positives (non-rivers detected)

### Terrain Quality
- **Excellent**: Filled terrain is indistinguishable from surrounding land
- **Good**: Minor visible artifacts that wouldn't bother most players
- **Acceptable**: Noticeable artifacts but terrain is functional
- **Fail**: Obvious scars, floating blocks, or terrain that looks broken

### Bank Transitions
- **Excellent**: No additional smoothing needed
- **Good**: Minor smoothing needed at edges
- **Acceptable**: Significant smoothing logic required but feasible
- **Fail**: Artifacts too severe to smooth programmatically

### Performance
- **Excellent**: Faster than current biome filter approach
- **Good**: Comparable to current approach
- **Acceptable**: Slightly slower but not noticeable during play
- **Fail**: Noticeable lag during chunk generation

## Results

*Not yet run.*

## Conclusions

*Pending results.*

## Files

**Create:**
- `src/main/java/org/sosly/rivertale/mixin/RiverErasurePocMixin.java`

**Modify:**
- `src/main/resources/rivertale.mixins.json` — Add the PoC mixin

**Disable (rename with `.disabled` extension):**
- `src/main/resources/data/minecraft/worldgen/density_function/overworld/ridges_folded.json`
- `src/main/resources/data/lithosphere/worldgen/density_function/river_valleys.json`
- Comment out `MultiNoiseBiomeSourceMixin` in mixins.json