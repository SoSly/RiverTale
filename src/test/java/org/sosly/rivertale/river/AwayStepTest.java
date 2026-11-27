package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AwayStepTest {
    private final Map<CellPos, Cell> cells = new HashMap<>();
    private CommonConfig originalConfig;
    private RiverWorld world;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(64, 1, 1, true, false, 12, 5, 10, 0.25, 1, 0.5,
            SourceConfig.DEFAULT, CacheConfig.DEFAULT, false, 64, 4, 10, Integer.MAX_VALUE));
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
    void acceptsExactlyTenConsecutiveStepsAway() {
        List<CellPos> expected = detour(10);
        connect(expected);
        FlowlineTracer tracer = tracer(last(expected), expected.get(0));

        Flowline path = tracer.trace(expected.get(0));

        assertTrue(path.isValid());
        assertEquals(expected, path.cells());
        assertEquals(10, Store.getDistribution(FlowlineTracer.class, "awaySteps").snapshot().max());
        assertEquals(10, Store.getDistribution(FlowlineTracer.class, "maxConsecutiveAwaySteps").snapshot().max());
        UpstreamSearch upstream = new UpstreamSearch(tracer);
        upstream.expand(List.of(last(expected)));
        assertEquals(expected.size(), upstream.cellCount());
    }

    @Test
    void rejectsTheEleventhStepAndReverseDiscoveryUsesTheSameLimit() {
        List<CellPos> expected = detour(11);
        connect(expected);
        FlowlineTracer tracer = tracer(last(expected), expected.get(0), expected.get(1));

        Flowline path = tracer.trace(expected.get(0));

        assertFalse(path.isValid());
        assertTrue(tracer.trace(expected.get(1)).isValid());
        UpstreamSearch upstream = new UpstreamSearch(tracer);
        upstream.expand(List.of(last(expected)));
        assertEquals(expected.size() - 1, upstream.cellCount());
        upstream.expand(List.of(expected.get(10)));
        assertEquals(expected.size(), upstream.cellCount());
    }

    @Test
    void aCloserStepResetsTheAllowanceForAnotherTenStepDetour() {
        List<CellPos> expected = new ArrayList<>();
        expected.add(new CellPos(20, 20));
        move(expected, FlowDirection.WEST, 10);
        move(expected, FlowDirection.NORTHEAST, 1);
        move(expected, FlowDirection.WEST, 10);
        move(expected, FlowDirection.NORTHEAST, 1);
        move(expected, FlowDirection.EAST, 38);
        move(expected, FlowDirection.SOUTH, 2);
        connect(expected);
        FlowlineTracer tracer = tracer(last(expected), expected.get(0));

        Flowline path = tracer.trace(expected.get(0));

        assertTrue(path.isValid());
        assertEquals(expected, path.cells());
        assertEquals(20, Store.getDistribution(FlowlineTracer.class, "awaySteps").snapshot().max());
        assertEquals(10, Store.getDistribution(FlowlineTracer.class, "maxConsecutiveAwaySteps").snapshot().max());
        UpstreamSearch upstream = new UpstreamSearch(tracer);
        upstream.expand(List.of(last(expected)));
        assertEquals(expected.size(), upstream.cellCount());
    }

    @Test
    void equalDistanceStepsConsumeAllowanceInsteadOfResettingIt() {
        List<CellPos> expected = new ArrayList<>();
        expected.add(new CellPos(39, 40));
        move(expected, FlowDirection.NORTHWEST, 9);
        move(expected, FlowDirection.NORTHEAST, 1);
        move(expected, FlowDirection.NORTH, 1);
        move(expected, FlowDirection.SOUTHEAST, 9);
        move(expected, FlowDirection.SOUTH, 2);
        connect(expected);
        FlowlineTracer tracer = tracer(last(expected), expected.get(0), expected.get(1));

        Flowline path = tracer.trace(expected.get(0));

        assertFalse(path.isValid());
        assertTrue(tracer.trace(expected.get(1)).isValid());
    }

    @Test
    void backtrackingRestoresAllowanceAndRevisitsAnExhaustedCellWithMoreRemaining() {
        List<CellPos> expected = retryRoute();
        FlowlineTracer tracer = tracer(last(expected), expected.get(0));

        Flowline path = tracer.trace(expected.get(0));

        assertTrue(path.isValid());
        assertEquals(expected, path.cells());
        assertFalse(path.cells().contains(new CellPos(19, 20)));
    }

    @Test
    void concurrentRetriesKeepTheirAllowanceIndependent() {
        List<CellPos> expected = retryRoute();
        FlowlineTracer tracer = tracer(last(expected), expected.get(0));

        List<Flowline> paths = IntStream.range(0, 24).parallel()
            .mapToObj(index -> tracer.trace(expected.get(0))).toList();

        assertTrue(paths.stream().allMatch(Flowline::isValid));
        assertTrue(paths.stream().allMatch(path -> path.cells().equals(expected)));
    }

    @Test
    void activeCellsRemainForbiddenEvenWithAwayAllowanceRemaining() {
        CellPos source = new CellPos(20, 20);
        CellPos next = new CellPos(19, 20);
        flow(source, 100, List.of(FlowDirection.WEST));
        flow(next, 90, List.of(FlowDirection.EAST));

        Flowline path = tracer(new CellPos(40, 20), source).trace(source);

        assertFalse(path.isValid());
        assertEquals(List.of(source), path.cells());
    }

    @Test
    void nudgesStillCannotMoveAwayEvenWhenTheirClimbWouldBeAllowed() {
        CommonConfig.set(new CommonConfig(64, 1, 1, true, true, 12, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        CellPos source = new CellPos(20, 20);
        List<CellPos> nudgedRoute = new ArrayList<>();
        nudgedRoute.add(new CellPos(19, 19));
        move(nudgedRoute, FlowDirection.EAST, 21);
        move(nudgedRoute, FlowDirection.SOUTH, 1);
        connect(nudgedRoute);
        flow(source, 100, List.of(FlowDirection.WEST));
        flow(new CellPos(19, 20), 90, List.of());
        flow(nudgedRoute.get(0), 110, List.of(FlowDirection.EAST));
        FlowlineTracer tracer = tracer(last(nudgedRoute), source);

        assertFalse(tracer.data().steps(source).stream().anyMatch(step -> step.pos().equals(nudgedRoute.get(0))));
        assertFalse(tracer.predecessors(nudgedRoute.get(0)).contains(source));
    }

    @Test
    void crossingARegionBorderDoesNotResetTheAllowance() {
        List<CellPos> expected = new ArrayList<>();
        expected.add(new CellPos(70, 20));
        move(expected, FlowDirection.SOUTHWEST, 11);
        move(expected, FlowDirection.NORTH, 31);
        move(expected, FlowDirection.EAST, 4);
        connect(expected);
        Region destination = new Region(new RegionPos(0, 0), RegionType.COASTAL,
            List.of(new Boundary(last(expected), last(expected).relative(FlowDirection.SOUTH), BoundaryType.BASIN)));
        Region fluvial = new Region(new RegionPos(1, 0), RegionType.FLUVIAL);
        FlowlineTracer tracer = prepare(List.of(destination, fluvial), expected.get(0), expected.get(1));

        Flowline path = tracer.trace(expected.get(0));

        assertFalse(path.isValid());
        assertTrue(tracer.trace(expected.get(1)).isValid());
    }

    @Test
    void aLoopDependentFailureIsNotCachedWhenAnOutletRemainsReachable() {
        List<CellPos> expected = new ArrayList<>();
        CellPos source = new CellPos(20, 20);
        CellPos branch = new CellPos(19, 20);
        expected.add(source);
        expected.add(branch);
        move(expected, FlowDirection.NORTHEAST, 1);
        move(expected, FlowDirection.EAST, 20);
        move(expected, FlowDirection.SOUTH, 1);
        connect(expected);
        flow(branch, 180, List.of(FlowDirection.NORTH, FlowDirection.NORTHEAST));
        flow(new CellPos(19, 19), 170, List.of(FlowDirection.SOUTH));

        Flowline path = tracer(last(expected), source).trace(source);

        assertTrue(path.isValid());
        assertEquals(expected, path.cells());
    }

    private List<CellPos> retryRoute() {
        List<CellPos> firstAttempt = new ArrayList<>();
        firstAttempt.add(new CellPos(20, 20));
        move(firstAttempt, FlowDirection.WEST, 10);
        move(firstAttempt, FlowDirection.NORTHWEST, 1);
        connect(firstAttempt);
        List<CellPos> expected = new ArrayList<>();
        expected.add(new CellPos(20, 20));
        move(expected, FlowDirection.NORTHWEST, 1);
        move(expected, FlowDirection.WEST, 9);
        move(expected, FlowDirection.SOUTH, 1);
        move(expected, FlowDirection.NORTHWEST, 1);
        move(expected, FlowDirection.NORTHEAST, 1);
        move(expected, FlowDirection.EAST, 30);
        move(expected, FlowDirection.SOUTH, 2);
        connect(expected);
        flow(expected.get(0), 300, List.of(FlowDirection.WEST, FlowDirection.NORTHWEST));
        return expected;
    }

    private List<CellPos> detour(int count) {
        List<CellPos> path = new ArrayList<>();
        path.add(new CellPos(20, 20));
        move(path, FlowDirection.WEST, count);
        move(path, FlowDirection.NORTHEAST, 1);
        move(path, FlowDirection.EAST, 19 + count);
        move(path, FlowDirection.SOUTH, 1);
        return path;
    }

    private void move(List<CellPos> path, FlowDirection direction, int count) {
        for (int i = 0; i < count; i++) {
            path.add(last(path).relative(direction));
        }
    }

    private CellPos last(List<CellPos> path) {
        return path.get(path.size() - 1);
    }

    private void connect(List<CellPos> path) {
        assertEquals(path.size(), new HashSet<>(path).size());
        for (int i = 0; i < path.size() - 1; i++) {
            CellPos from = path.get(i);
            CellPos to = path.get(i + 1);
            flow(from, 200 - i, List.of(FlowDirection.fromDelta(to.x() - from.x(), to.z() - from.z())));
        }
    }

    private void flow(CellPos pos, int height, List<FlowDirection> directions) {
        Cell cell = mock(Cell.class);
        when(cell.averageEstimatedTerrainHeight()).thenReturn(height);
        when(cell.flowDirections()).thenReturn(directions);
        when(cell.feature()).thenReturn(Feature.NONE);
        cells.put(pos, cell);
    }

    private FlowlineTracer tracer(CellPos terminus, CellPos... sources) {
        Region region = new Region(terminus.getRegion(), RegionType.INLAND,
            List.of(new Boundary(terminus, terminus.relative(FlowDirection.SOUTH), BoundaryType.BASIN)));
        return prepare(List.of(region), sources);
    }

    private FlowlineTracer prepare(List<Region> regions, CellPos... sources) {
        Cell empty = mock(Cell.class);
        when(empty.flowDirections()).thenReturn(List.of());
        when(empty.feature()).thenReturn(Feature.NONE);
        RiverWorld.configureRouting(0, CommonConfig.get().awayStepAllowance());
        return world.regions(regions).cells(pos -> cells.getOrDefault(pos, empty)).prepare(regions.get(0).pos(), sources);
    }
}
