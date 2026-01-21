---
level: 5
parent: "[[Spatial Infrastructure]]"
status: draft
---

# Sample Enrichment — Implementation

This document describes changes to how Sample enrichment works. Cell no longer holds Sample references—it holds sample positions and queries SampleCache. Cell handles lazy enrichment when accessing source classification fields.

## Scope

This implementation covers:
- Changing Cell from `Map<ChunkPos, Sample>` to `Set<ChunkPos>` for sample positions
- Updating Cell's averaging methods to query SampleCache
- Adding per-field enrichment in Cell's averaging methods
- Adding `provider()` method to SampleCache
- Adding per-field `sample(field, pos)` method to SampleProvider
- Adding DensityField enum
- Adding per-field `with*` mutation methods to Sample

This does NOT cover:
- Changes to core field sampling (unchanged)
- Changes to SampleCache's `getOrCompute()` (unchanged)
- Changes to how CellCache creates cells (only the data stored changes)

## Why This Change

The previous design had Cell hold `Map<ChunkPos, Sample>` references. When samples were enriched and updated in SampleCache, Cell's references became stale. This made lazy enrichment unreliable.

The new design:
1. Cell holds `Set<ChunkPos>` positions only
2. Cell queries SampleCache when it needs sample data
3. Cell enriches samples on demand and updates SampleCache
4. SampleCache is always the source of truth

## Definition of Done

- Cell stores `samplePositions: Set<ChunkPos>` instead of `samples: Map<ChunkPos, Sample>`
- Cell's averaging methods query SampleCache for each position
- Cell's averaging methods for enriched fields (temperature, erosion, ridges, vegetation) enrich samples on demand
- SampleCache exposes `provider()` for individual field sampling
- Sample has `withTemperature()`, `withErosion()`, `withRidges()`, `withVegetation()` mutation methods
- Unit tests pass

## Implementation Sequence

```
Phase 1: Sample Changes
    └── Add with* mutation methods for each enriched field

Phase 2: SampleCache and SampleProvider Changes
    ├── Add DensityField enum
    ├── Add sample(field, pos) to SampleProvider
    ├── Remove sampleFull() from SampleProvider
    ├── Update NoiseBasedSampleProvider
    └── Add provider() accessor to SampleCache

Phase 3: Cell Changes
    ├── Change samples field to samplePositions
    ├── Update constructors
    ├── Update averaging methods for core fields
    └── Update averaging methods for enriched fields (with lazy enrichment)

Phase 4: CellCache Changes
    └── Update cell creation to store positions only
```

---

## Phase 1: Sample Changes

**File:** `Sample.java`

Add mutation methods for each enriched field:

```
withTemperature(temperature: double): Sample
    return new Sample(pos, continents, depth, erosion, ridges, temperature, vegetation)

withErosion(erosion: double): Sample
    return new Sample(pos, continents, depth, erosion, ridges, temperature, vegetation)

withRidges(ridges: double): Sample
    return new Sample(pos, continents, depth, erosion, ridges, temperature, vegetation)

withVegetation(vegetation: double): Sample
    return new Sample(pos, continents, depth, erosion, ridges, temperature, vegetation)
```

---

## Phase 2: SampleCache and SampleProvider Changes

### 2.1 SampleProvider Interface

**File:** `SampleProvider.java`

Add per-field sampling method:

**Before:**
```
interface SampleProvider {
    sampleCore(pos: ChunkPos): Sample
    sampleFull(pos: ChunkPos): Sample
}
```

**After:**
```
interface SampleProvider {
    sampleCore(pos: ChunkPos): Sample
    sample(field: DensityField, pos: ChunkPos): double
}
```

Remove `sampleFull()` — no longer needed since fields are sampled individually.

### 2.2 DensityField Enum

**New file:** `DensityField.java`

```
enum DensityField {
    CONTINENTS,
    DEPTH,
    EROSION,
    RIDGES,
    TEMPERATURE,
    VEGETATION
}
```

### 2.3 NoiseBasedSampleProvider

**File:** `NoiseBasedSampleProvider.java`

Add per-field sampling:

```
sample(field: DensityField, pos: ChunkPos): double
    return switch field:
        case CONTINENTS -> sampleNoise(CONTINENTS_NOISE, pos)
        case DEPTH -> sampleNoise(DEPTH_NOISE, pos)
        case EROSION -> sampleNoise(EROSION_NOISE, pos)
        case RIDGES -> sampleNoise(RIDGES_NOISE, pos)
        case TEMPERATURE -> sampleNoise(TEMPERATURE_NOISE, pos)
        case VEGETATION -> sampleNoise(VEGETATION_NOISE, pos)
```

### 2.4 SampleCache

**File:** `SampleCache.java`

Add accessor for the provider:

```
provider(): SampleProvider
    return this.provider
```

This allows Cell to sample individual fields during enrichment.

---

## Phase 3: Cell Changes

**File:** `Cell.java`

### 3.1 Change Field Type

**Before:**
```
Map<ChunkPos, Sample> samples
```

**After:**
```
Set<ChunkPos> samplePositions
```

### 3.2 Update Constructors

**Minimal constructor:**
```
Cell(CellPos pos, Set<ChunkPos> samplePositions):
    this(pos, samplePositions, List.of(), Feature.NONE,
         null, null, null, null, null, null, null, null, null, null)
```

### 3.3 Update Core Field Averaging Methods

```
averageContinents(): double
    sum = 0
    for pos in samplePositions:
        sample = SampleCache.get().getOrCompute(pos)
        sum += sample.continents
    return sum / samplePositions.size()

averageDepth(): double
    sum = 0
    for pos in samplePositions:
        sample = SampleCache.get().getOrCompute(pos)
        sum += sample.depth
    return sum / samplePositions.size()

averageEstimatedTerrainHeight(): int
    return (int) Math.round(70 + 144 * averageDepth())
```

### 3.4 Update Enriched Field Averaging Methods

These methods enrich samples on demand:

```
averageTemperature(): double
    sum = 0
    for pos in samplePositions:
        sample = SampleCache.get().getOrCompute(pos)
        if sample.temperature == null:
            temp = SampleCache.get().provider().sample(TEMPERATURE, pos)
            sample = sample.withTemperature(temp)
            SampleCache.get().put(pos, sample)
        sum += sample.temperature
    return sum / samplePositions.size()
```

Same pattern for `averageErosion()`, `averageRidges()`, `averageVegetation()`.

### 3.5 Update isOcean and isBasin

```
isOcean(): boolean
    for pos in samplePositions:
        sample = SampleCache.get().getOrCompute(pos)
        if not sample.isOcean():
            return false
    return true

isBasin(): boolean
    if isOcean():
        return false
    seaLevel = WorldSettings.get().seaLevel()
    for pos in samplePositions:
        sample = SampleCache.get().getOrCompute(pos)
        if sample.estimatedTerrainHeight() >= seaLevel:
            return false
    return true
```

---

## Phase 4: CellCache Changes

**File:** `CellCache.java`

### 4.1 Update collectSamples

**Before:** `collectSamples(pos: CellPos): Map<ChunkPos, Sample>`

**After:** `collectSamplePositions(pos: CellPos): Set<ChunkPos>`

```
collectSamplePositions(pos: CellPos): Set<ChunkPos>
    offsets = getSamplePositions(config.cellSize(), config.samplesPerCell())
    positions = new HashSet<>()

    cellMinChunkX = pos.x() * config.cellSize()
    cellMinChunkZ = pos.z() * config.cellSize()

    for offset in offsets:
        chunkX = cellMinChunkX + offset.x
        chunkZ = cellMinChunkZ + offset.z
        positions.add(new ChunkPos(chunkX, chunkZ))

    return positions
```

Note: This no longer fetches samples from SampleCache. Cell will query them when needed.

### 4.2 Update getOrCompute

```
getOrCompute(pos: CellPos): Cell
    key = pos.toLong()
    cell = cache.get(key)
    if cell != null:
        return cell

    samplePositions = collectSamplePositions(pos)
    cell = new Cell(pos, samplePositions)
    cache.put(key, cell)
    return cell
```

---

## Validation Checklist

| Test | Setup | Expected |
|------|-------|----------|
| Core field averaging | Cell with 4 sample positions | Returns average of values from SampleCache |
| Enriched field averaging | Cell with unenriched samples | Enriches samples, updates cache, returns average |
| Repeated enriched access | Call averageTemperature() twice | Second call uses cached enriched values |
| isOcean all ocean | All samples have continents < threshold | true |
| isOcean mixed | One sample is land | false |
| isBasin below sea level | All samples below 63, none ocean | true |

---

## Migration Notes

### Breaking Changes

- `Cell.samples` removed, replaced with `Cell.samplePositions`
- `collectSamples()` renamed to `collectSamplePositions()`, returns `Set<ChunkPos>`
- `SampleProvider.sampleFull()` removed, use `sample(field, pos)` instead
- Any code that accessed `cell.samples` directly must be updated

### Dependencies

Requires:
- SampleCache with `provider()` accessor
- Sample with `with*` mutation methods
- SampleProvider with per-field `sample(field, pos)` method
- DensityField enum

### Performance Considerations

Cell now queries SampleCache on every averaging call. For frequently-accessed cells, this adds cache lookups. However:
- SampleCache is a fast in-memory cache
- Enrichment only happens once per sample per field
- The alternative (stale references) was incorrect, not just slow
