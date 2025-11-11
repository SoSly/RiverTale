# RiverTale Implementation Roadmap

## Milestone 1: Vanilla River Suppression ✅
**Goal**: Stop vanilla rivers from generating

**Status**: COMPLETE

### Technical Context
Vanilla rivers are biomes (`minecraft:river`, `minecraft:frozen_river`) placed by `MultiNoiseBiomeSource`. They're flat at Y=63 with no actual water flow simulation.

### Implementation
- Created `RiverTaleBiomeSource extends MultiNoiseBiomeSource` class
- Override `getNoiseBiome()` to intercept river biome placement
- Replace river biomes with appropriate land biome from surrounding climate
- Installed our biome source via dimension datapack override

### Validation
✅ Generate new world with mod installed - no vanilla rivers appear
✅ `/locate biome river` finds nothing
✅ Other biomes generate normally

---

## Milestone 2: Noise-Based River System
**Goal**: Implement core river generation using noise functions with flow, elevation, and width variation

### Technical Context
We'll use ridge noise (1.0 - abs(simplex)) to create naturally connected, branching river patterns. Rivers will be modulated by continentalness with flow direction calculated from gradients. This milestone establishes the foundation for all river properties.

### Issues
1. **Implement RiverNoiseGenerator** - Create ridge noise generator that produces connected river patterns. Test: /rivertale debug shows river potential overlay
2. **Add continentalness modulation** - Modulate rivers by continentalness values. Test: Rivers appear in mid-continental zones (0.0-0.4)
3. **Inject river biomes in RiverTaleBiomeSource** - Override getNoiseBiome to place rivers. Test: /locate biome river finds generated rivers
4. **Calculate flow direction from gradient** - Compute flow using continentalness gradient. Test: Rivers flow consistently toward ocean without reversals
5. **Calculate river elevation from continentalness** - Map continentalness to elevation. Test: Rivers at sea level near ocean, higher inland
6. **Vary river width by distance from ocean** - Base width on inverse continentalness. Test: Rivers narrow at sources, wide near ocean

### Success Criteria
- Rivers form connected branching patterns
- Flow consistently toward ocean
- Appropriate elevation changes from coast to inland
- Natural width variation based on position

---

## Milestone 3: Terrain Integration
**Goal**: Carve riverbeds, place water at varying elevations, and implement river merging

### Technical Context
This milestone integrates rivers with the terrain system. We'll modify density functions to carve channels, override surface rules for water placement, and handle confluences where rivers meet.

### Issues
1. **Implement density function carving** - Lower terrain near rivers via density functions. Test: Visible carved channels in terrain
2. **Place water at variable elevation** - Override surface rules for water placement. Test: Water fills channels at calculated elevation
3. **Implement river merging at confluences** - Widen rivers where they meet. Test: Rivers grow wider at Y-junctions

### Success Criteria
- Natural-looking valleys visible in terrain
- Water correctly placed at varying elevations
- Smooth merging at river confluences
- No floating terrain or water escaping banks

---

## Milestone 4: Advanced Features
**Goal**: Add lakes and waterfalls for more realistic river systems

### Technical Context
These features add realism by detecting special terrain conditions. Lakes form in inland depressions (not coastal bays), while waterfalls appear at sharp elevation changes.

### Issues
1. **Detect inland basins for lakes** - Find local minima away from ocean. Test: Lakes only in inland depressions, not coastal bays
2. **Generate waterfalls at elevation drops** - Create vertical water at sharp drops. Test: Waterfalls appear at cliffs with vertical water columns

### Success Criteria
- Lakes only in true inland depressions
- Waterfalls at appropriate cliff locations
- Natural-looking water features
- Rivers can flow into lakes

---

## Milestone 5: Polish & Release
**Goal**: Configuration, performance optimization, and debug tools

### Technical Context
Final polish to make the mod production-ready. All parameters should be configurable, performance should be optimized, and debug tools should be finalized for troubleshooting.

### Issues
1. **Add configuration system** - Make all parameters configurable. Test: Config file controls river generation parameters
2. **Optimize performance** - Cache calculations and optimize hot paths. Test: <10% generation slowdown vs vanilla
3. **Finalize debug overlay system** - Complete unified debug visualization. Test: /rivertale debug shows all river properties

### Success Criteria
- All major parameters configurable
- Minimal performance impact
- Comprehensive debug visualization
- Clean, helpful logs
- Stable memory usage

---

## Debug System

The `/rivertale debug` command provides a unified visualization overlay that progressively shows:
- **River potential** (red=high, blue=low) - ridge noise patterns showing connected river networks
- **Flow persistence** (arrows) - consistent flow direction across chunks
- **Elevation gradient** (blue=sea level, red=high elevation) - water surface height
- **Width variation** (line thickness) - how wide rivers are
- **Confluence zones** (highlighted areas) - where rivers merge

Each milestone adds new data to this single debug view, making it easy to see all river properties at once.

## Testing Strategy

Each issue includes specific testable validation criteria. The debug overlay provides immediate visual validation while test worlds verify functionality.

## Development Philosophy

- **Chunk-local calculations**: Everything computed on-demand
- **No global state**: Pure functions from coordinates
- **Noise-based**: Deterministic patterns without pre-calculation
- **Incremental progress**: Each milestone delivers working functionality
- **Testable units**: Each issue has clear verification criteria
