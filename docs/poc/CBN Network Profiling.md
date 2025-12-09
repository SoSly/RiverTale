## Goal

Determine how much time building and querying a Cell-based Network adds to world generation. This validates our assumption that the recursive distance-to-ocean computation and cell caching strategy are performant enough for production use.

## Approach

Create a standalone CBN implementation that:

1. Samples `continentalness` from Minecraft's noise system to determine cell density
2. Builds cell data (participation, flow direction, distance-to-ocean) on demand
3. Caches computed cells
4. Simulates the query pattern that chunk generation would use

### Simulation Details

**Cell creation:**
- Sample a 7×7 grid of density values per cell (49 noise samples)
- Compare density to neighbors to determine flow direction
- Recursively query downstream cells for distance-to-ocean

**Query pattern:**
- For each chunk, determine which cell(s) it touches
- Query those cells for river data
- Measure both cold (uncached) and warm (cached) query times

**Network scale:**
- Simulate exploring outward from spawn (0,0)
- Track cache size as exploration expands
- Test with 4096-block cells (Pass 1 scale)
- Test first-access chains of varying lengths (5, 15, 25 cells to ocean)

### What to Measure

- Time to compute a single cell (density sampling + neighbor comparison)
- Time for recursive distance-to-ocean query (cold, uncached chain)
- Time for cached cell lookup
- Memory footprint per cached cell
- Total cache size after N chunks generated

## Success Criteria

**Per-cell computation (cold):**
- **Acceptable**: < 5ms per cell
- **Good**: < 2ms per cell
- **Excellent**: < 1ms per cell

**Distance-to-ocean chain (cold, 20 cells):**
- **Acceptable**: < 100ms total
- **Good**: < 50ms total
- **Excellent**: < 20ms total

**Cached lookup:**
- **Acceptable**: < 0.1ms per lookup
- **Good**: < 0.01ms per lookup

**Memory:**
- **Acceptable**: < 200 bytes per cell
- **Good**: < 100 bytes per cell

If cold chain computation exceeds 200ms, we may need to compute distance asynchronously or pre-warm caches.

## Results

*Not yet run.*

## Conclusions

*Pending results.*
