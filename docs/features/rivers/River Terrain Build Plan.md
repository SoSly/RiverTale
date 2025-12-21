---
level: 5
parent: "[[River Terrain Architecture]]"
status: draft
---

# River Terrain Implementation Plan

This document outlines the build order for the river terrain system described in `River Terrain Architecture.md`. The Region-based Network (CBN) is complete and provides flow direction, distance, upstream count, and river paths. This plan covers turning that data into visible rivers.

## Key Decisions

| Decision | Value | Rationale |
|----------|-------|-----------|
| Injection point | TAIL of `fillFromNoise()` | We're terrain shaping, not surface decoration; SURFACE phase then decorates our embankments |
| Embankment material | Stone (`defaultBlock`) | Surface rules automatically apply biome materials |
| Heightmap strategy | `primeHeightmaps()` after all mods | Simpler than per-block updates; we're modifying many blocks anyway |
| Path source | CBN's `riverPath` field | Computed deterministically; no state needed |
| Water placement timing | FEATURES phase | Terrain is final, surface rules complete, caves carved |
| Water placement data | Re-query CBN | Stateless design; path math is cheap, CBN data is deterministic |
| Region features | SOURCE, PATH, CONFLUENCE, TERMINUS | Position in network determines category |
| Region features | BASIN | Operates at region level, not cell |
| Biome overlay | Mixin to `LevelChunkSection` | Expose biome setter; apply `minecraft:river` during terrain modification |
| Structure handling | Carve through | Rivers are authoritative; structures in path get carved/flooded |
| Cave intersection | Accept as-is | Source blocks don't drain; caves carved after us add natural terrain variation |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                    RiverCellManager (CBN)                       │
│  - Provides: riverPath, distanceToTerminus, upstreamCount       │
│  - Provides: regionType, primaryOutput                       │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    RiverTerrainProcessor                        │
│  - Called from fillFromNoise() mixin                            │
│  - Iterates cells, determines categories, invokes features   │
│  - Handles region-level BASIN separately from cell features    │
└─────────────────────────────────────────────────────────────────┘
                              │
                    ┌─────────┴─────────┐
                    ▼                   ▼
┌───────────────────────────┐ ┌───────────────────────────────────┐
│    TerrainModifier        │ │        FeatureRegistry            │
│  - ChannelCarver          │ │  Region features:                │
│  - ValleyCarver           │ │  - StreamFeature (SOURCE)         │
│  - EmbankmentBuilder      │ │  - RiverFeature (PATH)            │
│  - (shared utilities)     │ │  - JunctionFeature (CONFLUENCE)   │
└───────────────────────────┘ │  - MouthFeature (TERMINUS)        │
                              │  Region feature:                    │
                              │  - EndorheicFeature (BASIN)       │
                              └───────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    RiverWaterFeature                            │
│  - Registered world feature, runs during FEATURES phase         │
│  - Re-queries CBN for river data, fills channels with water     │
└─────────────────────────────────────────────────────────────────┘
```

## Feature Categories

From the Architecture doc, features are categorized by network position:

| Category | Level | Basic Feature | Location |
|----------|-------|---------------|----------|
| SOURCE | Region | Stream | First cell of a river (no inlets) |
| PATH | Region | River | Standard segments along the river |
| CONFLUENCE | Region | Junction | Region with 2+ inlets merging |
| TERMINUS | Region | Mouth | Last cell before ocean/lake/basin |
| BASIN | Region | Endorheic | Region-level pool at flow minimum |

**Basin termination flow:**
When a river reaches a basin region:
1. Entry cell receives TERMINUS category → Mouth feature
2. Region receives BASIN category → Endorheic feature
3. The mouth flows into the basin pool

## Implementation Phases

### Phase 1: Infrastructure + Injection

**Goal:** Move CBN to the right injection point and wire up basic terrain processing.

**Files:**
- `src/main/java/org/sosly/rivertale/mixin/NoiseBasedChunkGeneratorMixin.java` (modify existing `ChunkGeneratorMixin`)
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/RiverTerrainProcessor.java`

**CBN Timing Change:**

Currently CBN is computed at HEAD of `buildSurface()`. We need it at TAIL of `fillFromNoise()` so terrain modification can use it.

```
// Before: ChunkGeneratorMixin injects at HEAD of buildSurface()
// After: Inject at TAIL of fillFromNoise()

// Move this logic into the new injection point:
onFillFromNoiseComplete(blender, randomState, structureManager, chunk):
    // 1. Compute CBN for cells overlapping this chunk (moved from buildSurface)
    for each region overlapping chunk:
        RiverCellManager.getOrCreate(cellKey, provider, worldSeed, savedData)

    // 2. Process terrain (new)
    RiverTerrainProcessor.process(region, chunk, randomState)
```

**Basic RiverTerrainProcessor:**

```
RiverTerrainProcessor.process(region, chunk, randomState):
    chunkPos = chunk.pos
    level = region.level

    cells = RiverCellManager.getCellsInChunk(level, chunkPos)

    for region in regions:
        if not region.isParticipating: continue
        if region.riverPath is null or empty: continue

        // For now, just log that we found river cells
        log("Found river region {} with {} path segments", region.key, region.riverPath.size)

    // Heightmap recalculation (no-op for now, but structure in place)
    Heightmap.primeHeightmaps(chunk, [WORLD_SURFACE_WG, OCEAN_FLOOR_WG])
```

**Validation:**
1. Build compiles
2. Create new world
3. Check logs: "Found river region..." messages appear during chunk generation
4. No errors, no visual changes yet

---

### Phase 2: Basic River Carving

**Goal:** Carve visible trenches along river paths. First visual test.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/ChannelCarver.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/PathInterpolator.java`

**ChannelCarver (simplified):**

```
ChannelCarver.carve(chunk, centerX, centerZ, waterY, width, depth):
    radius = width / 2

    for dx from -radius to radius:
        for dz from -radius to radius:
            dist = sqrt(dx² + dz²)
            if dist > radius: continue

            x = centerX + dx
            z = centerZ + dz

            if not isInChunk(chunk, x, z): continue

            // Simple flat-bottom trench for now
            floorY = waterY - depth

            for y from floorY to chunk.maxBuildHeight:
                pos = BlockPos(x, y, z)
                if y <= floorY:
                    chunk.setBlockState(pos, STONE)
                else:
                    chunk.setBlockState(pos, AIR)
```

**PathInterpolator (minimal):**

```
cellToWorld(region, cellRow, cellCol) -> BlockPos:
    regionWorldX = region.key.regionX * REGION_SIZE
    regionWorldZ = region.key.regionZ * REGION_SIZE
    cellSize = REGION_SIZE / 8

    return BlockPos(
        cellWorldX + (cellCol * cellSize) + (cellSize / 2),
        0,
        cellWorldZ + (cellRow * cellSize) + (cellSize / 2)
    )
```

**Updated RiverTerrainProcessor:**

```
RiverTerrainProcessor.process(region, chunk, randomState):
    chunkPos = chunk.pos
    level = region.level
    cells = RiverCellManager.getCellsInChunk(level, chunkPos)

    // Fixed width/depth for now
    WIDTH = 8
    DEPTH = 4
    ELEVATION = 64  // Fixed elevation for testing

    for region in regions:
        if not region.isParticipating: continue
        if region.riverPath is null or empty: continue

        for cell in region.riverPath:
            center = cellToWorld(region, cell.row, cell.col)

            if not isInChunk(chunk, center.x, center.z): continue

            ChannelCarver.carve(chunk, center.x, center.z, ELEVATION, WIDTH, DEPTH)

    Heightmap.primeHeightmaps(chunk, [WORLD_SURFACE_WG, OCEAN_FLOOR_WG])
```

**Validation:**
1. Create new world
2. Walk to a river area (use `/rivertale locate river`)
3. **See trenches carved into terrain at y=64**
4. Trenches follow river paths
5. Trenches are ~8 blocks wide, ~4 blocks deep

---

### Phase 3: Water Placement

**Goal:** Fill carved channels with water. Rivers are now visible and wet.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/RiverWaterFeature.java`

**RiverWaterFeature:**

```
RiverWaterFeature extends Feature:
    place(context) -> bool:
        level = context.level
        chunkPos = ChunkPos(context.origin)
        chunk = level.getChunk(chunkPos.x, chunkPos.z)

        cells = RiverCellManager.getCellsInChunk(level, chunkPos)

        // Same fixed values as Phase 2
        WIDTH = 8
        DEPTH = 4
        ELEVATION = 64

        placedAny = false

        for region in regions:
            if not region.isParticipating: continue
            if region.riverPath is null or empty: continue

            for cell in region.riverPath:
                center = cellToWorld(region, cell.row, cell.col)
                if not isInChunk(chunk, center.x, center.z): continue

                placedAny |= fillWaterColumn(chunk, center.x, center.z,
                                              ELEVATION, ELEVATION - DEPTH, WIDTH)

        return placedAny


    fillWaterColumn(chunk, centerX, centerZ, surfaceY, floorY, width) -> bool:
        placed = false
        radius = width / 2

        for dx from -radius to radius:
            for dz from -radius to radius:
                dist = sqrt(dx² + dz²)
                if dist > radius: continue

                x = centerX + dx
                z = centerZ + dz
                if not isInChunk(chunk, x, z): continue

                for y from surfaceY down to (floorY + 1):
                    pos = BlockPos(x, y, z)
                    if chunk.getBlockState(pos).isAir:
                        chunk.setBlockState(pos, WATER)
                        placed = true

        return placed
```

**Feature Registration:**

```
// Register feature and add to all biomes
// Use GenerationStep.Decoration.LAKES
```

**Validation:**
1. Create new world
2. Walk to river area
3. **Rivers are filled with water**
4. Water sits at y=64
5. Water is stable (no draining)

---

### Phase 4: Width and Depth Scaling

**Goal:** Rivers get wider downstream based on upstream count.

**Files:**
- Update `PathInterpolator.java` with width/depth/elevation calculations

**Add to PathInterpolator:**

```
calculateElevation(distanceToTerminus) -> int:
    return SEA_LEVEL + (distanceToTerminus * ELEVATION_PER_CELL)

calculateWidth(upstreamCount) -> int:
    baseWidth = MIN_WIDTH + WIDTH_PER_ACC_LOG * log(1 + upstreamCount)
    return min(baseWidth, MAX_WIDTH)

calculateDepth(width) -> int:
    widthRatio = (width - MIN_WIDTH) / (MAX_WIDTH - MIN_WIDTH)
    return lerp(MIN_CHANNEL_DEPTH, MAX_CHANNEL_DEPTH, widthRatio)
```

**Update RiverTerrainProcessor:**

```
// Replace fixed values with calculated values:
width = calculateWidth(cell.upstreamCount)
depth = calculateDepth(width)
elevation = calculateElevation(cell.distanceToTerminus)
```

**Update RiverWaterFeature similarly.**

**Validation:**
1. Create new world
2. Find a long river
3. **River is narrow at source, wider downstream**
4. River elevation decreases toward ocean
5. Depth scales with width

---

### Phase 5: Valley Carving + Embankments

**Goal:** Rivers cut through hills and build up in valleys.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/ValleyCarver.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/EmbankmentBuilder.java`

**ValleyCarver:**

```
ValleyCarver.carve(chunk, region, centerX, centerZ, waterY, width, valleySlope):
    searchRadius = width / 2 + MAX_SEARCH_EXTENSION

    for dx from -searchRadius to searchRadius:
        for dz from -searchRadius to searchRadius:
            x = centerX + dx
            z = centerZ + dz

            if not isInChunk(chunk, x, z): continue

            dist = sqrt(dx² + dz²)
            distFromChannel = max(0, dist - width / 2)
            valleyFloorY = waterY + (distFromChannel / valleySlope)

            terrainY = region.getHeight(WORLD_SURFACE_WG, x, z)
            if terrainY <= valleyFloorY: continue

            for y from valleyFloorY to terrainY:
                chunk.setBlockState(BlockPos(x, y, z), AIR)
```

**EmbankmentBuilder:**

```
EmbankmentBuilder.build(chunk, region, centerX, centerZ, waterY, width, embankmentSlope):
    searchRadius = width / 2 + MAX_SEARCH_EXTENSION

    for dx from -searchRadius to searchRadius:
        for dz from -searchRadius to searchRadius:
            x = centerX + dx
            z = centerZ + dz

            if not isInChunk(chunk, x, z): continue

            dist = sqrt(dx² + dz²)
            distFromChannel = max(0, dist - width / 2)
            embankmentTopY = waterY - (distFromChannel / embankmentSlope)

            terrainY = region.getHeight(WORLD_SURFACE_WG, x, z)
            if terrainY >= embankmentTopY: continue

            for y from (terrainY + 1) to embankmentTopY:
                chunk.setBlockState(BlockPos(x, y, z), STONE)
```

**Update RiverTerrainProcessor to call these based on terrain height.**

**Validation:**
1. Create new world with varied terrain (amplified or custom)
2. Find a river crossing hills
3. **River cuts a valley through high terrain**
4. Find a river crossing low terrain
5. **River builds embankments above low terrain**
6. Embankments get grass/sand from surface rules

---

### Phase 6: Feature System

**Goal:** Refactor carving into the feature abstraction without changing visual output.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/RiverFeatureCategory.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/RiverFeature.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/BasinFeature.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/RegionContext.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/RegionContext.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/RiverFeatureRegistry.java`
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/features/RiverPathFeature.java`

**RiverFeatureCategory:**

```
enum RiverFeatureCategory:
    SOURCE        // First cell, no inlets
    PATH          // Standard river segments
    CONFLUENCE    // 2+ inlets merging
    TERMINUS      // Last cell before ending
    BASIN         // Region-level pool
```

**RegionContext:**

```
RegionContext:
    cellOrigin: BlockPos
    cellSize: int
    entryPoint: BlockPos
    exitPoint: BlockPos
    pathPoints: List<BlockPos>
    targetElevation: int
    width: int
    depth: int
    category: RiverFeatureCategory
    biome: Holder<Biome>
    noise: RandomSource
    additionalInlets: List<BlockPos>
```

**RiverFeature interface:**

```
interface RiverFeature:
    getCategory() -> RiverFeatureCategory
    modifyTerrain(context: RegionContext, chunk: ChunkAccess, region: WorldGenRegion)
```

**RiverPathFeature:** Move current carving logic into this feature for PATH category.

**Update RiverTerrainProcessor to use feature registry and determine categories.**

**Validation:**
1. Create new world
2. **Visual output identical to Phase 5**
3. Verify registry logging shows feature selection
4. All river types still work

---

### Phase 7: SOURCE Feature (StreamFeature)

**Goal:** Handle river sources distinctly (MVP: same as PATH).

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/features/StreamFeature.java`

**Implementation:** Copy of RiverPathFeature, but registered for SOURCE category. Future: add spring pools, narrower channels.

**Validation:**
1. Find a source region (`/rivertale locate source`)
2. Channel begins correctly at first cell
3. No visual artifacts

---

### Phase 8: CONFLUENCE Feature (JunctionFeature)

**Goal:** Handle river merges with wider confluence area.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/features/JunctionFeature.java`

**Implementation:** Wider carving at merge point, routes from all inlets to merge point.

**Validation:**
1. Find a confluence (`/rivertale locate confluence`)
2. **Wider area carved at junction**
3. All inlet channels connect smoothly

---

### Phase 9: TERMINUS Feature (MouthFeature)

**Goal:** Handle river mouths where they meet ocean/lake/basin.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/features/MouthFeature.java`

**Implementation:** Slight widening at terminus, no embankments.

**Validation:**
1. Find a coastal cell (`/rivertale locate coastal`)
2. **River widens slightly at mouth**
3. Connects smoothly to ocean

---

### Phase 10: BASIN Feature (EndorheicFeature)

**Goal:** Create cell-level basin pools.

**Files:**
- `src/main/java/org/sosly/rivertale/worldgen/river/terrain/features/EndorheicFeature.java`
- Update RiverWaterFeature for basin water

**Implementation:** Circular pool at cell center, depth decreases toward edges.

**Validation:**
1. Find a basin cell (`/rivertale locate basin`)
2. **Pool carved at cell**
3. Pool filled with water
4. Rivers flow into pool

---

### Phase 11: Biome Overlay

**Goal:** Apply `minecraft:river` biome for correct mob spawning and sounds.

**Files:**
- `src/main/java/org/sosly/rivertale/mixin/LevelChunkSectionMixin.java`

**Biome Mixin:**

```
// Mixin: LevelChunkSectionMixin
// Target: LevelChunkSection
// Shadow: biomes (PalettedContainer<Holder<Biome>>)

setBiome(quartX, quartY, quartZ, biome):
    biomes.set(quartX, quartY, quartZ, biome)
```

**Apply during terrain modification.**

**Validation:**
1. Create new world
2. Stand in river
3. **F3 shows river biome**
4. Correct ambient sounds

---

### Phase 12: Configuration

**Goal:** Expose tuning parameters.

**Files:**
- `src/main/java/org/sosly/rivertale/config/RiverTerrainConfig.java`

**Configurable Values:**

```
TerrainConfig:
    SEA_LEVEL = 63
    ELEVATION_PER_CELL = 4
    MIN_WIDTH = 2
    MAX_WIDTH = 40
    MIN_CHANNEL_DEPTH = 3
    MAX_CHANNEL_DEPTH = 16
    WIDTH_PER_ACC_LOG = 20.0
    EMBANKMENT_SLOPE = 40
    VALLEY_SLOPE = 30
    MAX_SEARCH_EXTENSION = 60
    MIN_POOL_RADIUS = 50
    MAX_POOL_RADIUS = 400
    BASIN_DEPTH = 5
    CONFLUENCE_RADIUS_MULT = 1.5
    POST_MERGE_WIDTH_BONUS = 0.1
    MOUTH_WIDTH_MULT = 1.2
```

**Validation:**
1. Change config values
2. Create new world
3. **Rivers reflect new configuration**

---

## Testing Milestones

| After Phase | Testable Behavior |
|-------------|-------------------|
| 1 | Logs show "Found river region..." during chunk gen |
| 2 | **Visible trenches carved at y=64** |
| 3 | **Rivers filled with water** |
| 4 | Rivers widen downstream, elevation varies |
| 5 | Rivers cut valleys, build embankments |
| 6 | Same output, using feature system |
| 7 | Source cells work correctly |
| 8 | Confluence cells have wider merge areas |
| 9 | River mouths widen at coast |
| 10 | Basin pools carved and filled |
| 11 | River biome applied correctly |
| 12 | Configuration affects generation |

---

## Integration with CBN

**Data consumed from CBN:**

| Field | Usage |
|-------|-------|
| `riverPath` | Cell sequence for iteration and path interpolation |
| `distanceToTerminus` | Calculate target elevation |
| `upstreamCount` | Calculate river width |
| `regionType` | Detect Shore for TERMINUS, Basin for BASIN feature |
| `primaryOutput` | Determine exit direction for path endpoints |

