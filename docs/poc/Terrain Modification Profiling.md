## Goal

Determine how much time injecting terrain modification into the chunk generation pipeline adds to world generation. This validates our assumption that block-level terrain modification between NOISE and SURFACE is performant enough for production use.

## Approach

Create a minimal mixin that:

1. Injects at HEAD of `NoiseBasedChunkGenerator.buildSurface()`
2. Randomly decides whether to modify terrain (simulating ~2% river coverage)
3. For "river" chunks, modifies 500-3000 blocks (simulating channel carving and embankments)
4. Recalculates heightmaps after modification
5. Measures elapsed time for the modification step

The modification doesn't need to be coherent—random block changes are fine. We're measuring the *cost* of modification, not the *correctness*.

### What to Measure

- Time spent in our injected code per chunk (with and without modification)
- Total chunk generation time (baseline vs. with mixin)
- Heightmap recalculation time specifically
- Memory allocation during modification (if easily measurable)

### Test Conditions

- Generate a new world and fly around to trigger chunk generation
- Generate 1000+ chunks to get stable averages
- Test with different modification rates (1%, 2%, 5%, 10%)
- Test with different block counts (500, 1500, 3000)

## Success Criteria

- **Acceptable**: River chunks add < 20% to per-chunk generation time
- **Good**: River chunks add < 10% to per-chunk generation time
- **Excellent**: River chunks add < 5% to per-chunk generation time

Dry chunks (no river) should add negligible overhead (< 1ms).

If modification adds > 30% to chunk generation time, we need to reconsider the approach or identify optimization targets.

## Results

*Not yet run.*

## Conclusions

*Pending results.*
