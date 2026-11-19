package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sosly.rivertale.cell.Cell;
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
import org.sosly.rivertale.world.RiverShaping;
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
            SourceConfig.DEFAULT, CacheConfig.DEFAULT, true));
        Store.clear();
        regions.put(source.getRegion(), new Region(source.getRegion(), RegionType.INLAND));
        regions.put(new RegionPos(2, 0), new Region(new RegionPos(2, 0), RegionType.FLUVIAL));
        regions.put(outlet.getRegion(), new Region(outlet.getRegion(), RegionType.COASTAL,
            List.of(new Boundary(outlet, new CellPos(25, 3), BoundaryType.OCEAN))));
        for (int x = source.x(); x < outlet.x(); x++) {
            CellPos pos = new CellPos(x, 3);
            cells.put(pos, new Cell(pos, Set.of()).withFlowDirections(List.of(FlowDirection.EAST)));
        }
        cells.put(source, cells.get(source).withFeature(Feature.SNOWMELT));
    }

    @AfterEach
    void tearDown() {
        CommonConfig.set(original);
        Store.clear();
    }

    @Test
    void inlandSourceAtTheEdgeOfTheInitialNineHasAllDownstreamDataBeforeTracing() {
        RegionPos center = new RegionPos(0, 0);
        Set<RegionPos> visible = visible(center);
        PreparedRiverData data = RiverPreparation.prepare(center, visible, true, graph());

        assertTrue(data.regions().containsKey(new RegionPos(2, 0)));
        assertTrue(data.regions().containsKey(new RegionPos(3, 0)));
        assertEquals(0, Store.getTimer(FlowlineTracer.class, "trace").count());

        WatershedPreview.Result result = traceWithoutLoaders(center, visible, data);
        Flowline path = result.paths().get(source.getRegion()).get(0);
        assertTrue(path.isValid());
        assertEquals(source, path.cells().get(0));
        assertEquals(outlet, path.terminus());
        assertEquals(1, result.watersheds().size());
    }

    @Test
    void remoteUpstreamsAndWatershedGatheringUseOnlyPreparedData() {
        CommonConfig c = CommonConfig.get();
        CommonConfig.set(new CommonConfig(c.regionSize(), c.cellSize(), c.samplesPerCell(), true, false, 12, 0, 0,
            SourceConfig.DEFAULT, CacheConfig.DEFAULT, false));
        RegionPos center = outlet.getRegion();
        Set<RegionPos> visible = visible(center);
        assertFalse(visible.contains(source.getRegion()));
        PreparedRiverData data = RiverPreparation.prepare(center, visible, false, graph());

        WatershedPreview.Result result = traceWithoutLoaders(center, visible, data);

        assertEquals(1, result.watersheds().size());
        Watershed watershed = result.watersheds().get(0);
        assertTrue(watershed.contains(source));
        assertEquals(outlet, watershed.terminus(source).orElseThrow());
    }

    @Test
    void retryingADeadEndCannotLoadOrReclassifyCells() {
        cells.put(source, cells.get(source).withFlowDirections(List.of(FlowDirection.EAST, FlowDirection.SOUTHEAST)));
        cells.put(new CellPos(16, 3), new Cell(new CellPos(16, 3), Set.of()));
        cells.put(new CellPos(16, 4), new Cell(new CellPos(16, 4), Set.of())
            .withFlowDirections(List.of(FlowDirection.NORTHEAST)));
        RegionPos center = new RegionPos(0, 0);
        Set<RegionPos> visible = visible(center);
        PreparedRiverData data = RiverPreparation.prepare(center, visible, true, graph());

        WatershedPreview.Result result = traceWithoutLoaders(center, visible, data);

        Flowline path = result.paths().get(source.getRegion()).get(0);
        assertEquals(new CellPos(16, 4), path.cells().get(1));
        assertEquals(outlet, path.terminus());
    }

    @Test
    void preparedPathsSurviveChangesToThePreparationProviders() {
        RegionPos center = new RegionPos(0, 0);
        Set<RegionPos> visible = visible(center);
        PreparedRiverData data = RiverPreparation.prepare(center, visible, true, graph());
        cells.clear();
        regions.clear();

        WatershedPreview.Result result = traceWithoutLoaders(center, visible, data);

        assertEquals(outlet, result.paths().get(source.getRegion()).get(0).terminus());
    }


    @Test
    void downstreamDiagonalTributaryAndDetoursArePreparedBeforeTracing() {
        CommonConfig c = CommonConfig.get();
        CommonConfig.set(new CommonConfig(c.regionSize(), c.cellSize(), c.samplesPerCell(), true, false, 12, 0, 10,
            SourceConfig.DEFAULT, CacheConfig.DEFAULT, false));
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
        cells.put(source, cells.get(source).withFeature(Feature.SNOWMELT));
        cells.put(remoteSource, cells.get(remoteSource).withFeature(Feature.SNOWMELT));
        RegionPos center = source.getRegion();
        Set<RegionPos> visible = visible(center);
        assertFalse(visible.contains(remoteSource.getRegion()));

        PreparedRiverData data = RiverPreparation.prepare(center, visible, false, graph());
        WatershedPreview.Result result = traceWithoutLoaders(center, visible, data);

        assertEquals(1, result.watersheds().size());
        Watershed watershed = result.watersheds().get(0);
        assertTrue(watershed.contains(source));
        assertTrue(watershed.contains(remoteSource));
        assertTrue(watershed.terminus(source).isPresent());
        assertTrue(watershed.terminus(remoteSource).isPresent());
        assertTrue(Store.getDistribution(FlowlineTracer.class, "awaySteps").snapshot().max() > 0);
    }

    private void flowing(CellPos pos, FlowDirection direction) {
        cells.put(pos, new Cell(pos, Set.of()).withFlowDirections(List.of(direction)));
    }

    private WatershedPreview.Result traceWithoutLoaders(RegionPos center, Set<RegionPos> visible, PreparedRiverData data) {
        tracing.set(true);
        try (MockedStatic<RegionCache> cache = mockStatic(RegionCache.class, call -> forbidden());
             MockedStatic<RegionExplorer> explorer = mockStatic(RegionExplorer.class, call -> forbidden());
             MockedStatic<SampleCache> samples = mockStatic(SampleCache.class, call -> forbidden());
             MockedStatic<RiverShaping> shaping = mockStatic(RiverShaping.class, call -> forbidden());
             MockedStatic<Feature> features = mockStatic(Feature.class, call -> forbidden())) {
            WatershedPreview.Result result = WatershedPreview.tracePrepared(center, visible, new FlowlineTracer(data));
            for (RegionPos pos : visible) {
                assertEquals(pos.toLong(), result.encode(pos).getLong("pos"));
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
            return cells.getOrDefault(pos, new Cell(pos, Set.of()));
        }, 0, CommonConfig.get().awayStepAllowance());
    }

    private Set<RegionPos> visible(RegionPos center) {
        Set<RegionPos> result = new HashSet<>();
        result.add(center);
        for (FlowDirection direction : FlowDirection.D8) {
            result.add(center.relative(direction));
        }
        return result;
    }
}
