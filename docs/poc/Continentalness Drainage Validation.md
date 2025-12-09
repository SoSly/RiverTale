## Goal

Verify that Minecraft's `continentalness` noise parameter reliably decreases toward oceans and produces sensible drainage patterns when used as the basis for CBN flow direction. This validates the core assumption that density-based flow will create rivers that actually reach the sea.

## Approach

Sample `continentalness` across a large area and visualize the results to verify:

1. Values are lowest at/near ocean
2. Values generally increase inland
3. Flow direction (toward lowest neighbor) creates connected paths to ocean
4. No large isolated basins unless terrain actually supports them

### Sampling Strategy

**Cell density calculation:**
- For each cell, sample continentalness at 49 points (7×7 subcell grid)
- Average the 49 samples to derive the cell's density value
- This matches how CBN will actually compute cell density

**Grid coverage:**
- Test with configurable cell sizes (512, 1024, 2048, 4096 blocks)
- Cover a large area (e.g., -50,000 to +50,000 on both axes)
- Record averaged density value for each cell

**Flow simulation:**
- For each cell, compare its density to its four cardinal neighbors
- Flow direction points toward the lowest-density participating neighbor
- Trace the flow path until reaching ocean (density below threshold) or a basin
- Record path length and whether it reaches ocean

### What to Measure

- Distribution of continentalness values (histogram)
- Percentage of cells that successfully drain to ocean
- Average/max path length to ocean
- Number and size of endorheic basins (cells that don't reach ocean)
- Any chaotic regions where flow direction oscillates or loops

### Visualization

Generate a map showing:
- Continentalness values as color gradient (blue = ocean, green/brown = land)
- Flow direction arrows
- Traced drainage paths
- Basin locations

## Success Criteria

- **Success**: >90% of land cells drain to ocean; flow directions are consistent and sensible; basins are rare and geographically plausible
- **Partial success**: 70-90% drain to ocean; some chaotic regions but mostly coherent
- **Failure**: <70% drain to ocean; widespread chaotic flow; many artificial basins

If this fails, we may need to:
1. Use a different noise parameter or combination of parameters
2. Apply smoothing or filtering to continentalness values
3. Implement basin-filling logic to force drainage
4. Reconsider whether noise-based density is viable for flow direction

## Results

*Not yet run.*

## Conclusions

*Pending results.*