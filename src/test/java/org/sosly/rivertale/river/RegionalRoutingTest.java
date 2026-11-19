package org.sosly.rivertale.river;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionalRoutingTest {
    private final CellPos firstShore = new CellPos(7, 7);
    private final CellPos secondShore = new CellPos(8, -1);
    private final Region first = new Region(new RegionPos(0, 0), RegionType.INLAND,
        List.of(new Boundary(firstShore, firstShore.relative(FlowDirection.SOUTH), BoundaryType.BASIN)));
    private final Region middle = new Region(new RegionPos(1, 0), RegionType.FLUVIAL);
    private final Region last = new Region(new RegionPos(1, -1), RegionType.COASTAL,
        List.of(new Boundary(secondShore, secondShore.relative(FlowDirection.NORTH), BoundaryType.OCEAN)));
    private final Map<CellPos, Cell> cells = new HashMap<>();
    private CommonConfig originalConfig;
    private RiverWorld world;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, true, false, 12, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT, false));
        Store.clear();
        world = new RiverWorld();
    }

    @AfterEach
    void tearDown() {
        world.close();
        CommonConfig.set(originalConfig);
        Store.clear();
    }

    @Test
    void rejectsACellLoopAndBacktracksToTheNextCandidate() {
        addLoopWithExit();
        Flowline path = tracer().trace(new CellPos(7, 2));

        assertTrue(path.isValid());
        assertEquals(List.of(new CellPos(7, 2), new CellPos(8, 3), new CellPos(8, 2),
            new CellPos(9, 1), new CellPos(9, 0), secondShore), path.cells());
        assertEquals(path.cells().size(), new HashSet<>(path.cells()).size());
        assertTrue(path.cells().stream().anyMatch(cell -> last.pos().contains(cell)));
    }

    @Test
    void aLoopWithoutAnExitFailsWithoutPoisoningOtherSourceSearches() {
        flow(8, 3, FlowDirection.NORTH);
        flow(8, 2, FlowDirection.SOUTH);
        FlowlineTracer tracer = tracer();

        Flowline firstAttempt = tracer.trace(new CellPos(8, 3));
        Flowline secondAttempt = tracer.trace(new CellPos(8, 2));

        assertFalse(firstAttempt.isValid());
        assertFalse(secondAttempt.isValid());
        assertEquals(List.of(new CellPos(8, 3)), firstAttempt.cells());
        assertEquals(List.of(new CellPos(8, 2)), secondAttempt.cells());
    }

    @Test
    void cannotReturnToAnUpstreamRegionAtADifferentCell() {
        flow(7, 3, FlowDirection.SOUTHEAST);
        flow(8, 4, FlowDirection.SOUTHWEST);
        flow(7, 5, FlowDirection.SOUTHEAST);
        flow(8, 6, FlowDirection.SOUTHWEST);

        Flowline path = tracer().trace(new CellPos(7, 3));

        assertFalse(path.isValid());
        assertEquals(List.of(new CellPos(7, 3)), path.cells());
    }

    @Test
    void arrivalAndLocalSourcesUseTheSameRegionalDestinationChoices() {
        addLoopWithExit();
        FlowlineTracer tracer = tracer();
        Flowline arriving = tracer.trace(new CellPos(7, 2));
        Flowline local = tracer.trace(new CellPos(8, 3));

        assertTrue(arriving.isValid());
        assertTrue(local.isValid());
        assertEquals(secondShore, arriving.terminus());
        assertEquals(secondShore, local.terminus());
        assertEquals(arriving.cells().subList(1, arriving.cells().size()), local.cells());
    }

    @Test
    void reverseDiscoveryFollowsMovesAcrossRegionalDestinationChanges() {
        addLoopWithExit();
        FlowlineTracer tracer = tracer();
        UpstreamSearch search = new UpstreamSearch(tracer);

        assertTrue(tracer.predecessors(new CellPos(8, 3)).contains(new CellPos(7, 2)));
        assertFalse(tracer.predecessors(new CellPos(7, 2)).contains(new CellPos(8, 3)));
        Set<RegionPos> found = search.expand(List.of(secondShore));

        assertEquals(Set.of(first.pos(), middle.pos(), last.pos()), found);
        assertEquals(6, search.cellCount());
        assertTrue(search.expand(List.of(secondShore)).isEmpty());
    }

    @Test
    void concurrentColdSearchesShareRegionalRulesWithoutSharingActivePaths() {
        addLoopWithExit();
        FlowlineTracer tracer = tracer();
        List<Flowline> paths = IntStream.range(0, 40).parallel()
            .mapToObj(index -> tracer.trace(new CellPos(7, 2))).toList();

        assertTrue(paths.get(0).isValid());
        assertTrue(paths.stream().allMatch(paths.get(0)::equals));
    }

    private void addLoopWithExit() {
        flow(7, 2, FlowDirection.SOUTHEAST);
        flow(8, 3, FlowDirection.NORTH);
        flow(8, 2, FlowDirection.SOUTH, FlowDirection.NORTHEAST);
        flow(9, 1, FlowDirection.NORTH);
        flow(9, 0, FlowDirection.NORTHWEST);
    }

    private void flow(int x, int z, FlowDirection... directions) {
        CellPos pos = new CellPos(x, z);
        cells.put(pos, RiverWorld.land(pos, RiverWorld.LAND_HEIGHT, Feature.NONE, List.of(directions)));
    }

    private FlowlineTracer tracer() {
        RiverWorld.configureRouting(0, 10);
        return world.regions(List.of(first, middle, last)).discover(this::regions)
            .cells(pos -> cells.getOrDefault(pos, RiverWorld.land(pos, RiverWorld.LAND_HEIGHT, Feature.NONE, List.of())))
            .prepare(last.pos(), new CellPos(7, 2), new CellPos(8, 3), new CellPos(8, 2), new CellPos(7, 3));
    }

    private Collection<Region> regions(RegionPos pos) {
        if (pos.equals(first.pos())) {
            return List.of(first, middle);
        }
        if (pos.equals(middle.pos())) {
            return List.of(middle, first, last);
        }
        if (pos.equals(last.pos())) {
            return List.of(last, middle);
        }
        return List.of();
    }
}
