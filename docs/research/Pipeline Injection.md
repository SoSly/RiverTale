Research into how RiverTale can inject river terrain modification into Minecraft's chunk generation pipeline.

## ChunkStatus Architecture

**ChunkStatus is a registry, not an enum.** Each status is registered via `Registry.register(BuiltInRegistries.CHUNK_STATUS, ...)`. Statuses form a linked list via their `parent` field.

**Status chain (in order):**
```
EMPTY → STRUCTURE_STARTS → STRUCTURE_REFERENCES → BIOMES → NOISE → SURFACE → CARVERS → FEATURES → INITIALIZE_LIGHT → LIGHT → SPAWN → FULL
```

Each status has:
- `parent` — previous status in the chain
- `generationTask` — the work to perform
- `range` — how many chunks around need to be at parent status
- `index` — computed as `parent.index + 1` at construction

**Key classes:**
- `ChunkStatus` — defines generation stages and their tasks
- `ChunkMap` — orchestrates chunk generation scheduling
- `ChunkHolder` — tracks a chunk's progress through statuses
- `NoiseBasedChunkGenerator` — implements the actual generation logic

## Status Progression Flow

1. `ChunkMap.scheduleChunkGeneration()` requests a chunk at a target status
2. `ChunkHolder.getOrScheduleFuture(status, chunkMap)` schedules the work
3. `ChunkStatus.generate()` invokes the `generationTask`
4. The task calls appropriate `ChunkGenerator` methods
5. `ProtoChunk.setStatus(status)` marks completion

## What NOISE and SURFACE Do

### NOISE Status

Calls `NoiseBasedChunkGenerator.fillFromNoise()`:
- Evaluates density functions per-block
- Places terrain blocks (stone, water, air)
- Updates `OCEAN_FLOOR_WG` and `WORLD_SURFACE_WG` heightmaps

After NOISE completes:
- Terrain shape exists
- Heightmaps reflect terrain surface
- Biomes are assigned (from earlier BIOMES status)
- Block states can be read and written

### SURFACE Status

Calls `NoiseBasedChunkGenerator.buildSurface()`:
- Applies surface rules (grass on dirt, sand on beaches, etc.)
- Uses heightmaps to find surface positions
- Biome-aware material selection

## Injection Options Evaluated

### Option 1: Register Custom ChunkStatus

Insert a new status between NOISE and SURFACE.

**Problems:**
- Status `index` is computed at construction from parent chain
- Inserting between NOISE and SURFACE wouldn't update SURFACE's index
- Would break `isOrAfter()` checks throughout vanilla code
- Requires patching SURFACE's parent field via reflection or mixin
- Registration timing is tricky (must happen before vanilla static init)

**Verdict:** Too fragile, high risk of breaking vanilla behavior.

### Option 2: Mixin into SURFACE Generation Task

Intercept the SURFACE status task to run our code first.

**Problems:**
- Task is a lambda defined in static initializer
- Would need to capture and wrap the original task
- Messy injection point

**Verdict:** Possible but awkward.

### Option 3: Mixin into NoiseBasedChunkGenerator.buildSurface() ✓

Inject at HEAD of `buildSurface()` to run river terrain modification before surface rules.

**Advantages:**
- Clean, surgical injection point
- Called exactly once per chunk during SURFACE status
- Terrain blocks exist, heightmaps populated, biomes assigned
- Surface rules run AFTER our modifications (may apply grass to embankments)
- No status chain manipulation required

**Implementation sketch:**
```java
@Mixin(NoiseBasedChunkGenerator.class)
public class NoiseBasedChunkGeneratorMixin {
    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void rivertale$carveRivers(WorldGenRegion level, StructureManager structureManager,
                                        RandomState random, ChunkAccess chunk, CallbackInfo ci) {
        // 1. Query CBN for river data in this chunk
        // 2. If no river, return early (fast path)
        // 3. Carve river channel following CBN path
        // 4. Build embankments if terrain is too low
        // 5. Heightmaps updated by our block modifications
        // 6. Return, let vanilla buildSurface apply surface rules
    }
}
```

**Verdict:** Recommended approach.

## Recommended Architecture

```
NOISE completes
    ↓
SURFACE status triggered
    ↓
buildSurface() called
    ↓
[RiverTale mixin runs first]
    - Query CBN cell data
    - Carve river valleys
    - Build embankments
    - Update heightmaps
    ↓
[Vanilla buildSurface continues]
    - Applies surface rules to modified terrain
    - Grass/dirt on embankments
    - Sand/gravel in riverbeds (if biome rules match)
    ↓
CARVERS status
    - Caves carved (below our rivers)
    ↓
FEATURES status
    - Water placement (our feature)
    - Vegetation, structures, etc.
```

## Resolved Questions

### Heightmap Updates ✓

**Finding:** Heightmaps are NOT automatically updated during worldgen. `NoiseBasedChunkGenerator.fillFromNoise()` manually calls `heightmap.update()` after each `setBlockState()`.

**Critical:** `SurfaceSystem.buildSurface()` uses `WORLD_SURFACE_WG` heightmap to find surface height (line 99, 106). If we modify terrain without updating heightmaps, surface rules will apply to wrong Y levels:
- Carving valleys → surface rules applied to empty air at old height
- Building embankments → new terrain gets no surface treatment

**Solution:** After all terrain modifications, call:
```java
Heightmap.primeHeightmaps(chunk, EnumSet.of(
    Heightmap.Types.WORLD_SURFACE_WG,
    Heightmap.Types.OCEAN_FLOOR_WG
));
```

This recalculates heightmaps from scratch by scanning the chunk top-to-bottom. Must be called BEFORE returning from our mixin, so vanilla `buildSurface()` sees correct heights.

Alternative: Call `heightmap.update(x, y, z, blockState)` after each individual block change. More efficient for sparse modifications, but `primeHeightmaps` is simpler and sufficient for our use case since we're modifying many blocks anyway.

### Embankment Materials ✓

**Finding:** Surface rules only apply to `defaultBlock` (stone in Overworld). The check is:
```java
if (blockstate == this.defaultBlock) {
    // apply surface rule
}
```

**Implication:** If we place **stone** for embankments, surface rules will automatically apply biome-appropriate top layers (grass, dirt, sand, etc.). This is exactly what we want.

If we placed dirt or other blocks, surface rules would skip them—they'd remain as-is.

**Solution:** Always use `NoiseGeneratorSettings.defaultBlock()` (typically stone) when building embankments. Vanilla surface rules handle the rest.

### Chunk Boundary Coordination ✓

**Finding:** During SURFACE status, chunks have access to neighbors within an 8-chunk radius (at NOISE status). The `WorldGenRegion` passed to `buildSurface()` provides read access to these neighbors.

From `ChunkStatus.SURFACE`:
```java
public static final ChunkStatus SURFACE = registerSimple("surface", NOISE, 8, PRE_FEATURES, ...
```

The `8` is the range—all chunks within 8 chunks of center are available.

**Coordination strategy:**

1. **Read neighbors, write center only**: We can sample terrain heights from neighboring chunks to understand the river context, but we only modify the chunk being processed.

2. **Deterministic CBN paths**: Each chunk independently computes the same river path from the world seed. No coordination needed—determinism guarantees consistency.

3. **Embankment slopes**: Gradual slopes (30-50 blocks) naturally cross chunk boundaries. Each chunk carves its portion of the slope. Since the slope formula is deterministic (based on world position), edges match automatically.

4. **Wide rivers**: A 40-block wide river spans at most 3 chunks (if centered on boundaries). Each chunk carves its portion. The CBN path provides identical coordinates to all chunks, so the carved sections align.

**Potential edge case:** If chunk A is generated before chunk B, and river carving in A affects terrain that B will later try to read—this shouldn't matter because:
- We're injecting at HEAD of `buildSurface()`
- At this point, all neighbors are at NOISE status (terrain exists)
- We only read neighbor terrain, not modify it
- Each chunk's modifications are independent

**No special coordination code needed.** Determinism handles it.

## References

- `ChunkStatus`: `net.minecraft.world.level.chunk.ChunkStatus`
- `ChunkMap`: `net.minecraft.server.level.ChunkMap`
- `NoiseBasedChunkGenerator`: `net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator`
- `ChunkAccess`: `net.minecraft.world.level.chunk.ChunkAccess`
