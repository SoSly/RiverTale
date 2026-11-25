package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionExplorer;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;

class PreparationBoundaryTest {
    private final CellPos source = new CellPos(15, 3);
    private final CellPos outlet = new CellPos(24, 3);
    private final Map<RegionPos, Region> regions = new HashMap<>();
    private final Map<CellPos, Cell> cells = new HashMap<>();
    private final AtomicBoolean tracing = new AtomicBoolean();
    private CommonConfig original;

    @BeforeEach
    void setUp() {
        original = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, true, false, 12, 0, 0,
            SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        Store.clear();
        CellCache.shutdown();
        CellCache.init();
        regions.put(source.getRegion(), new Region(source.getRegion(), RegionType.INLAND));
        regions.put(new RegionPos(2, 0), new Region(new RegionPos(2, 0), RegionType.FLUVIAL));
        regions.put(outlet.getRegion(), new Region(outlet.getRegion(), RegionType.COASTAL,
            List.of(new Boundary(outlet, new CellPos(25, 3), BoundaryType.OCEAN))));
        for (int x = source.x(); x < outlet.x(); x++) {
            CellPos pos = new CellPos(x, 3);
            cells.put(pos, land(pos, Feature.NONE, FlowDirection.EAST));
        }
        cells.put(source, land(source, Feature.SNOWMELT, FlowDirection.EAST));
    }

    @AfterEach
    void tearDown() {
        CellCache.shutdown();
        CommonConfig.set(original);
        Store.clear();
    }

    @Test
    void inlandSourceAtTheEdgeOfTheInitialNineHasAllDownstreamDataBeforeTracing() {
        RegionPos center = new RegionPos(0, 0);
        Set<RegionPos> roots = Set.copyOf(center.neighborhood());
        PreparedRiverData data = RiverPreparation.prepare(roots, graph());

        assertTrue(data.regions().containsKey(new RegionPos(2, 0)));
        assertTrue(data.regions().containsKey(new RegionPos(3, 0)));
        assertEquals(0, Store.getTimer(FlowlineTracer.class, "trace").count());

        Map<RegionPos, RiverRegion> result = traceWithoutLoaders(roots, data);
        RiverRegion edge = result.get(source.getRegion());
        Flowline path = edge.flowlines().get(0);
        assertTrue(path.isValid());
        assertEquals(source, path.cells().get(0));
        assertEquals(outlet, path.terminus());
        assertEquals(1, edge.watersheds().size());
        assertTrue(result.get(center).watersheds().isEmpty());
        assertFalse(result.containsKey(outlet.getRegion()));
    }

    @Test
    void remoteUpstreamsAndWatershedGatheringUseOnlyPreparedData() {
        CommonConfig.set(new CommonConfig(8, 1, 1, true, false, 12, 0, 0, 2.0, 2.0, 0.125,
            SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        RegionPos center = outlet.getRegion();
        Set<RegionPos> roots = Set.copyOf(center.neighborhood());
        assertFalse(roots.contains(source.getRegion()));
        PreparedRiverData data = RiverPreparation.prepare(roots, graph());

        RiverRegion result = traceWithoutLoaders(roots, data).get(center);

        assertEquals(1, result.watersheds().size());
        Watershed watershed = result.watersheds().get(0);
        assertTrue(watershed.contains(source));
        assertEquals(outlet, watershed.terminus(source).orElseThrow());
        assertEquals(RiverWorld.LAND_HEIGHT + 2 * (outlet.x() - source.x()), watershed.cellY(source).orElseThrow());
        assertEquals(RiverWorld.LAND_HEIGHT, watershed.cellY(outlet).orElseThrow());
        assertEquals(Feature.SNOWMELT, result.cells().get(source).feature());
        CellPos rapids = new CellPos(source.x() + 1, source.z());
        assertEquals(Feature.RAPIDS, result.cells().get(rapids).feature());
        assertEquals(Feature.RAPIDS, CellCache.get().getIfPresent(rapids).feature());
        assertEquals(Feature.NONE, data.cell(rapids).feature());
        assertEquals(2.0, watershed.flow(source).orElseThrow());
        assertEquals(3.125, watershed.flow(outlet).orElseThrow());
    }

    @Test
    void retryingADeadEndCannotLoadOrReclassifyCells() {
        cells.put(source, land(source, Feature.SNOWMELT, FlowDirection.EAST, FlowDirection.SOUTHEAST));
        cells.put(new CellPos(16, 3), land(new CellPos(16, 3), Feature.NONE));
        cells.put(new CellPos(16, 4), land(new CellPos(16, 4), Feature.NONE, FlowDirection.NORTHEAST));
        Set<RegionPos> roots = Set.of(source.getRegion());
        PreparedRiverData data = RiverPreparation.prepare(roots, graph());

        RiverRegion result = traceWithoutLoaders(roots, data).get(source.getRegion());

        Flowline path = result.flowlines().get(0);
        assertEquals(new CellPos(16, 4), path.cells().get(1));
        assertEquals(outlet, path.terminus());
    }

    @Test
    void preparedPathsSurviveChangesToThePreparationProviders() {
        Set<RegionPos> roots = Set.of(source.getRegion());
        PreparedRiverData data = RiverPreparation.prepare(roots, graph());
        cells.clear();
        regions.clear();

        RiverRegion result = traceWithoutLoaders(roots, data).get(source.getRegion());

        assertEquals(outlet, result.flowlines().get(0).terminus());
    }


    @Test
    void downstreamDiagonalTributaryAndDetoursArePreparedBeforeTracing() {
        CommonConfig c = CommonConfig.get();
        CommonConfig.set(new CommonConfig(c.regionSize(), c.cellSize(), c.samplesPerCell(), true, false, 12, 0, 10,
            SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        CellPos remoteSource = new CellPos(26, -1);
        CellPos mainOutlet = new CellPos(30, 4);
        CellPos sideOutlet = new CellPos(25, 6);
        regions.put(remoteSource.getRegion(), new Region(remoteSource.getRegion(), RegionType.FLUVIAL));
        regions.put(mainOutlet.getRegion(), new Region(mainOutlet.getRegion(), RegionType.COASTAL, List.of(
            new Boundary(mainOutlet, new CellPos(31, 4), BoundaryType.OCEAN),
            new Boundary(sideOutlet, new CellPos(25, 7), BoundaryType.OCEAN))));
        cells.clear();
        for (int x = 15; x < 25; x++) {
            flowing(new CellPos(x, 3), FlowDirection.EAST);
        }
        flowing(new CellPos(25, 3), FlowDirection.SOUTHEAST);
        for (int x = 26; x < 30; x++) {
            flowing(new CellPos(x, 4), FlowDirection.EAST);
        }
        for (int z = -1; z < 3; z++) {
            flowing(new CellPos(26, z), FlowDirection.SOUTH);
        }
        flowing(new CellPos(26, 3), FlowDirection.SOUTHWEST);
        flowing(new CellPos(25, 4), FlowDirection.SOUTH);
        flowing(new CellPos(25, 5), FlowDirection.SOUTH);
        cells.put(source, land(source, Feature.SNOWMELT, FlowDirection.EAST));
        cells.put(remoteSource, land(remoteSource, Feature.SNOWMELT, FlowDirection.SOUTH));
        RegionPos center = source.getRegion();
        Set<RegionPos> roots = Set.copyOf(center.neighborhood());
        assertFalse(roots.contains(remoteSource.getRegion()));

        PreparedRiverData data = RiverPreparation.prepare(roots, graph());
        RiverRegion result = traceWithoutLoaders(roots, data).get(center);

        assertEquals(1, result.watersheds().size());
        Watershed watershed = result.watersheds().get(0);
        assertTrue(watershed.contains(source));
        assertTrue(watershed.contains(remoteSource));
        assertTrue(watershed.terminus(source).isPresent());
        assertTrue(watershed.terminus(remoteSource).isPresent());
        assertTrue(Store.getDistribution(FlowlineTracer.class, "awaySteps").snapshot().max() > 0);
    }

    private void flowing(CellPos pos, FlowDirection direction) {
        cells.put(pos, land(pos, Feature.NONE, direction));
    }

    private Cell land(CellPos pos, Feature feature, FlowDirection... directions) {
        return RiverWorld.land(pos, RiverWorld.LAND_HEIGHT, feature, List.of(directions));
    }

    private Map<RegionPos, RiverRegion> traceWithoutLoaders(Set<RegionPos> roots, PreparedRiverData data) {
        tracing.set(true);
        try (MockedStatic<RegionCache> cache = mockStatic(RegionCache.class, call -> forbidden());
             MockedStatic<RegionExplorer> explorer = mockStatic(RegionExplorer.class, call -> forbidden());
             MockedStatic<SampleCache> samples = mockStatic(SampleCache.class, call -> forbidden())) {
            Map<RegionPos, RiverRegion> result = RiverNetwork.assemble(roots, data);
            assertEquals(roots, result.keySet());
            for (RegionPos pos : roots) {
                assertEquals(pos.toLong(), result.get(pos).encode().getLong("pos"));
            }
            return result;
        }
    }

    private Object forbidden() {
        throw new AssertionError("Tracing attempted to access a preparation provider");
    }

    private RiverGraph graph() {
        return new RiverGraph(pos -> {
            assertFalse(tracing.get());
            return regions.getOrDefault(pos, new Region(pos, RegionType.OCEANIC));
        }, pos -> {
            assertFalse(tracing.get());
            return List.copyOf(regions.values());
        }, pos -> {
            assertFalse(tracing.get());
            return cells.getOrDefault(pos, land(pos, Feature.NONE));
        }, 0, CommonConfig.get().awayStepAllowance());
    }
}
