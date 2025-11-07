# RiverTale Implementation Roadmap

## Milestone 1: Vanilla River Suppression
**Goal**: Prove we can stop vanilla rivers from generating

### Technical Context
Vanilla rivers are biomes (`minecraft:river`, `minecraft:frozen_river`) placed by `net.minecraft.world.level.biome.MultiNoiseBiomeSource`. No carving happens - they're just flat at Y=63.

### Tasks
1. Create `RiverTaleBiomeSource extends MultiNoiseBiomeSource` class
2. Override `getNoiseBiome()` to intercept when vanilla returns river biomes
3. Replace river biomes with appropriate land biome based on surrounding climate
4. Make Minecraft use our biome source via ONE of these approaches:
   - Option A: Create dimension datapack override (data/minecraft/dimension/overworld.json)
   - Option B: Mixin into ChunkGenerator creation to swap biome source
   - Option C: Register custom WorldPreset that uses our biome source
   - Start with Option A (simplest), fall back to others if needed

### Validation
- Generate new world with mod installed
- `/locate biome river` should find nothing
- Fly around - no rivers visible
- Other biomes still generate normally

### Success Criteria
- Zero river biomes in generated world
- No crashes or generation errors
- Clean logs showing our biome source is active

---

## Milestone 2: Voronoi Drainage Network
**Goal**: Build deterministic drainage system without placing any blocks

### Technical Context
We need a mathematical model of how water drains across continents. Voronoi cells give us natural-looking drainage basins. Each cell drains to its lowest neighbor, creating a network that rivers will follow. This all happens in memory - no blocks are placed yet.

### Tasks
1. Implement Voronoi cell generator using world seed (look at Worley noise implementations)
2. Assign elevation to cells based on distance from ocean biomes
3. Build drainage graph where each cell points to lowest adjacent cell
4. Store network in World Capability or saved data (persists with world)

### Validation
- Create debug command `/rivertale debug show-cells`
- Renders Voronoi cells as colored overlay
- Shows drainage arrows between cells
- Displays elevation values

### Success Criteria
- Same seed produces identical network
- Every cell has drainage path to ocean or basin
- Debug visualization clearly shows drainage flow

---

## Milestone 3: River Path Calculation
**Goal**: Convert drainage network into actual river coordinates

### Technical Context
The drainage network tells us which cells connect, but rivers need actual (x,z) coordinates. We trace paths from high-drainage cells to ocean, adding curves and calculating width based on how much water flows through each point.

### Tasks
1. Calculate flow accumulation (how many cells drain through each cell)
2. Identify high-flow paths as river candidates (threshold-based)
3. Convert cell-to-cell connections into smooth coordinate paths
4. Calculate river width proportional to accumulated drainage area

### Validation
- Extend debug command to show river paths
- `/rivertale debug show-rivers`
- Renders calculated river centerlines
- Shows width variations

### Success Criteria
- Rivers follow drainage network
- Smooth, natural-looking paths
- Width increases at confluences
- No disconnected segments

---

## Milestone 4: Simple River Biome Injection
**Goal**: Place river biomes along our paths (still flat, no carving)

### Technical Context
Now our custom biome source (from Milestone 1) needs to actually place rivers. When `getNoiseBiome()` is called, check if those coordinates are on a river path. If yes, return river biome. Rivers are still flat at Y=63 but follow our paths.

### Tasks
1. Query river network for nearest river at given coordinates
2. Check if point is within river width of centerline
3. Return river biome if on river, delegate to super otherwise
4. Cache lookups for performance (many queries per chunk)

### Validation
- Generate world with our river biomes
- Rivers appear as biome but still flat
- `/locate biome river` finds our rivers
- F3 shows river biome at correct locations

### Success Criteria
- Rivers visible as distinct biome
- Follow our calculated paths exactly
- No gaps or discontinuities
- Still at Y=63 (no elevation yet)

---

## Milestone 5: Density Function Carving
**Goal**: Actually carve riverbeds into terrain

### Technical Context
This is where rivers become valleys. Density functions control terrain shape during world generation. We need to inject our own density modifier that reduces terrain height near rivers. Requires mixins since Forge has no API for this. Look at how RTF does it via `MixinRandomState` or similar.

### Tasks
1. Mixin to `NoiseRouter` or `RandomState` to inject custom density function
2. Create density modifier that queries river network for nearby rivers
3. Reduce density (lower terrain) based on distance to river and river width
4. Use smooth falloff for natural-looking banks

### Validation
- Generate world with carved rivers
- Rivers have visible channels
- `/rivertale debug show-carve-depth`
- Displays carving depth at player position

### Success Criteria
- Rivers cut into terrain
- Smooth valley walls
- Depth proportional to river size
- No floating terrain artifacts

---

## Milestone 6: Variable Water Elevation
**Goal**: Fill rivers with water at correct height

### Technical Context
Vanilla assumes all water is at Y=63. We need water at varying elevations. Surface rules control block placement during generation. We'll need custom surface rules that check our river network for local water level instead of using sea level.

### Tasks
1. Store water elevation with each river segment in network
2. Create custom surface rule that queries local water height
3. Mixin to surface rule system to use our water levels
4. Handle transitions between different water elevations smoothly

### Validation
- Rivers filled with water
- `/rivertale debug show-water-level`
- Water follows terrain downhill
- No gaps or air pockets

### Success Criteria
- Water at correct elevation throughout river
- Smooth gradient from source to ocean
- No water escaping banks
- Proper flow appearance

---

## Milestone 7: Basin Detection
**Goal**: Identify and mark endorheic basins

### Technical Context
Some drainage cells don't reach ocean - they're local minima. These create endorheic (internal) basins. We need to detect these during network generation and decide how to handle them (lakes or breakthrough carving).

### Tasks
1. Detect cells with no ocean drainage
2. Group connected basins
3. Calculate basin properties (area, depth)
4. Mark for resolution strategy

### Validation
- `/rivertale debug show-basins`
- Highlights internal drainage areas
- Shows basin statistics
- Lists resolution strategy

### Success Criteria
- All basins detected
- Correct grouping of connected cells
- Accurate depth calculations
- No infinite loops

---

## Milestone 8: Lake Formation
**Goal**: Create lakes in small basins

### Technical Context
For small endorheic basins, create lakes. Calculate spillover elevation (lowest point on basin rim), fill with water to that height. Lakes have flat water surfaces unlike rivers.

### Tasks
1. Identify spillover elevation for basins
2. Create flat water surface at spillover height
3. Generate lake shoreline
4. Ensure water contains to basin

### Validation
- Generate world with lakes
- Lakes appear in basins
- Flat water surface
- No flooding beyond basin

### Success Criteria
- Lakes at correct elevation
- Natural-looking shorelines
- Water stays contained
- Rivers can flow into lakes

---

## Milestone 9: Waterfalls
**Goal**: Handle elevation drops

### Technical Context
When river elevation changes rapidly (cliffs), create waterfalls. Water maintains upper level until drop point, then falls vertically. Requires special handling in both carving and water placement.

### Tasks
1. Detect sudden elevation changes in river path
2. Maintain upper water level to drop point
3. Create vertical water column
4. Handle pool at base

### Validation
- Find/create waterfall locations
- `/rivertale debug teleport-waterfall`
- Verify water flows over edge
- Check base pool formation

### Success Criteria
- Clean water edge at top
- Vertical water column
- No floating water
- Natural-looking drop

---

## Milestone 10: Breakthrough Carving
**Goal**: Handle large basins by carving exits

### Technical Context
For large endorheic basins, carve a gorge through the lowest barrier to ocean. This creates dramatic canyons where rivers "broke through" geological barriers. Requires modifying terrain after initial generation.

### Tasks
1. Find lowest saddle point to ocean
2. Carve narrow gorge through barrier
3. Connect basin drainage to ocean
4. Make gorge visually distinct

### Validation
- Generate world with large basins
- Verify breakthrough channels exist
- Check water flows through gorge
- Confirm narrow, steep sides

### Success Criteria
- Basins drain properly
- Gorges look carved, not natural
- Water flows continuously through
- No terrain artifacts

---

## Milestone 11: Tributary Networks
**Goal**: Add smaller rivers feeding main rivers

### Technical Context
Not all high-drainage paths are main rivers. Some are tributaries that merge into larger rivers. Need to identify secondary paths and handle confluence points where rivers merge at correct elevations.

### Tasks
1. Identify secondary drainage paths
2. Generate tributary rivers
3. Handle confluence points
4. Proper elevation merging

### Validation
- Generate world with tributaries
- Verify they connect properly
- Check elevation at confluences
- Confirm natural appearance

### Success Criteria
- Tributaries feed main rivers
- Smooth confluences
- Width increases after merge
- No elevation conflicts

---

## Milestone 12: Performance & Polish
**Goal**: Production-ready implementation

### Tasks
1. Profile and optimize hot paths
2. Implement proper caching
3. Add configuration options
4. Create user documentation

### Validation
- Benchmark world generation time
- Test with large modpacks
- Verify all config options work
- Run stress tests

### Success Criteria
- < 20% generation slowdown
- Stable memory usage
- All features configurable
- Clean, helpful logs

---

## Testing Approach

Each milestone gets:
1. **Unit tests** for core logic (where possible)
2. **Debug commands** for in-game validation
3. **Test worlds** with specific seeds for regression testing
4. **Visual confirmation** via screenshots/video

## Development Order Rationale

This order ensures:
- Each milestone builds on proven foundation
- Early milestones are simple (biome replacement)
- Complex parts (carving, water) come after basics work
- Visible progress at each step
- Can ship early milestone as "basic rivers" if needed
