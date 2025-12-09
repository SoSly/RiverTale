## Goal

Verify that stone blocks placed during our terrain modification step receive biome-appropriate surface materials (grass, sand, snow, etc.) when vanilla surface rules run afterward. This validates our assumption that embankments will blend naturally with surrounding terrain without custom surface handling.

## Approach

Create a minimal mixin that:

1. Injects at HEAD of `NoiseBasedChunkGenerator.buildSurface()`
2. Places a column of stone blocks above existing terrain (simulating an embankment)
3. Recalculates heightmaps after placement
4. Returns and lets vanilla `buildSurface()` continue

After generation, inspect the placed blocks to see if surface rules applied.

### Test Cases

**Basic biome coverage:**
- Plains: expect grass block on top, dirt below
- Desert: expect sand on top
- Snowy plains: expect snow/grass
- Mountains: expect stone or grass depending on elevation
- Swamp: expect grass or mud

**Placement heights:**
- At terrain level (embankment flush with ground)
- 5 blocks above terrain
- 10 blocks above terrain

**Placement shapes:**
- Single column
- 5x5 platform
- Sloped surface (diagonal rise)

### What to Observe

- Does the top block become grass/sand/snow as appropriate?
- How many layers of surface material are applied?
- Does surface depth match surrounding natural terrain?
- Any edge cases where surface rules fail to apply?

## Success Criteria

- **Success**: Stone blocks placed before surface rules receive biome-appropriate surface treatment indistinguishable from natural terrain
- **Partial success**: Surface rules apply but with visible differences (wrong depth, missing layers)
- **Failure**: Surface rules don't apply to placed stone at all, or apply wrong biome's materials

If this fails, we need to either:
1. Apply surface materials ourselves during terrain modification
2. Find a different injection point that still allows surface rule application
3. Accept visible embankment seams and mitigate with decoration

## Results

*Not yet run.*

## Conclusions

*Pending results.*