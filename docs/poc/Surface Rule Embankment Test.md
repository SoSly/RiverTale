---
cellType: poc
validates: "[[River Terrain System]]"
assumption: "Stone blocks placed before surface rules receive biome-appropriate surface treatment"
status: success
---

## Goal

Verify that stone blocks placed during our terrain modification step receive biome-appropriate surface materials (grass, sand, snow, etc.) when vanilla surface rules run afterward. This validates our assumption that embankments will blend naturally with surrounding terrain without custom surface handling.

## Approach

Created a mixin that:

1. Injects at HEAD of `NoiseBasedChunkGenerator.buildSurface()`
2. Places a 5x5 stone platform 10 blocks above existing terrain at chunk (0,0)
3. Pre-initializes any empty chunk sections to avoid palette corruption
4. Recalculates heightmaps after placement
5. Returns and lets vanilla `buildSurface()` continue

Tested by generating new worlds with different seeds to hit various biomes.

### Implementation Note

Empty chunk sections (above terrain) must be initialized before placing blocks. Sections marked as "only air" have uninitialized palettes that cause deserialization errors when the chunk is sent to the client. Fix: touch each target section with an explicit air block before placing stone.

## Results

**Biomes tested:**
- Badlands: red sand/terracotta surface applied ✓
- Ocean: surface rules still applied to floating platform ✓

**Observations:**
- Top block receives biome-appropriate surface material
- Multiple layers applied (surface block + dirt layers + stone base)
- Surface depth matches surrounding natural terrain
- No visible seams or differences from natural generation

## Conclusions

**Assumption validated.** Stone blocks placed at HEAD of `buildSurface()` receive full biome-appropriate surface treatment when vanilla surface rules continue afterward.

This confirms our integration approach:
1. Inject terrain modification at HEAD of `buildSurface()`
2. Place stone for embankments
3. Recalculate heightmaps
4. Let vanilla handle surface materials

No custom surface handling required. Embankments will blend naturally with surrounding terrain.
