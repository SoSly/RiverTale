package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FlowlinePreparationTest {
    private CommonConfig originalConfig;
    private MockedStatic<RegionCache> cacheStatic;
    private FlowlineTracer tracer;
    private MockedStatic<SourceTrace> preview;
    private final Map<CellPos, List<CellPos>> incoming = new HashMap<>();
    private final Map<RegionPos, List<Flowline>> paths = new HashMap<>();
    private final Map<RegionPos, Integer> traceCounts = new HashMap<>();

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, true, true, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT, false));
        Store.clear();
        RegionCache cache = mock(RegionCache.class);
        when(cache.getOrCompute(any(RegionPos.class))).thenAnswer(call -> region(call.getArgument(0)));
        cacheStatic = mockStatic(RegionCache.class);
        cacheStatic.when(RegionCache::get).thenReturn(cache);
        tracer = mock(FlowlineTracer.class);
        when(tracer.region(any(RegionPos.class))).thenAnswer(call -> region(call.getArgument(0)));
        when(tracer.predecessors(any(CellPos.class))).thenAnswer(call -> incoming.getOrDefault(call.getArgument(0), List.of()));
        preview = mockStatic(SourceTrace.class);
        preview.when(() -> SourceTrace.trace(any(Region.class), any(FlowlineTracer.class))).thenAnswer(call -> {
            RegionPos pos = ((Region) call.getArgument(0)).pos();
            traceCounts.merge(pos, 1, Integer::sum);
            return paths.getOrDefault(pos, List.of());
        });
    }

    @AfterEach
    void tearDown() {
        preview.close();
        cacheStatic.close();
        Store.clear();
        CommonConfig.set(originalConfig);
    }

    @Test
    void followsContributorsOfContributorsBeyondTheVisibleNeighbors() {
        addPath(0, 0, 1);
        addPath(-1, -1, 0, 1);
        addPath(-2, -2, -1, 0);
        addPath(-3, -3, -2, -1);

        FlowlinePreparation.Result result = prepare(0);

        assertEquals(Set.of(pos(0), pos(-1), pos(-2), pos(-3)), result.upstream().keySet());
        assertEquals(paths.get(pos(-3)), result.upstream().get(pos(-3)));
        assertTrue(traceCounts.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void doesNotExpandThroughDownstreamSupportOrItsOtherTributaries() {
        addPath(0, 0, 1);
        addPath(-1, -1, 0, 1);
        addPath(2, 2, 1);
        addPath(3, 3, 2);

        FlowlinePreparation.Result result = prepare(0);

        assertEquals(Set.of(pos(0), pos(-1)), result.upstream().keySet());
        assertFalse(result.traced().containsKey(pos(3)));
    }

    @Test
    void permittedTerritoryWithoutAnIncomingPathDoesNotExpandTheSet() {
        addPath(-1, -1, -2);
        incoming.computeIfAbsent(pos(0).getMinCell(), key -> new ArrayList<>()).add(pos(-1).getMinCell());
        addPath(-3, -3, -2);

        FlowlinePreparation.Result result = prepare(0);

        assertEquals(Set.of(pos(0)), result.upstream().keySet());
        assertTrue(result.traced().containsKey(pos(-1)));
        assertFalse(result.traced().containsKey(pos(-3)));
    }

    @Test
    void rejectedCandidateCanContributeToARegionFoundLater() {
        addPath(-2, -2, -1);
        incoming.computeIfAbsent(pos(0).getMinCell(), key -> new ArrayList<>()).add(pos(-2).getMinCell());
        addPath(-1, -1, 0);

        assertEquals(Set.of(pos(0), pos(-1), pos(-2)), prepare(0).upstream().keySet());
        assertEquals(1, traceCounts.get(pos(-2)));
    }

    @Test
    void transitRegionsContributeEvenWhenTheirOwnSourcesDoNotReachTheTarget() {
        addPath(-2, -2, -1, 0);
        addPath(-1, -1, -2);
        addPath(-3, -3, -2, -1);

        assertEquals(Set.of(pos(0), pos(-1), pos(-2), pos(-3)), prepare(0).upstream().keySet());
    }

    @Test
    void regionReentryIncludesTheReturningBranchButNotItsDownstreamSuffix() {
        addPath(0, 0, 1, 0, -1);
        addPath(2, 2, 1);
        addPath(-2, -2, -1);

        assertEquals(Set.of(pos(0), pos(1), pos(2)), prepare(0).upstream().keySet());
    }

    @Test
    void regionalCyclesFinishWithoutDuplicatingRivers() {
        addPath(0, 0, 1);
        addPath(1, 1, 0);

        FlowlinePreparation.Result result = prepare(0);

        assertEquals(Set.of(pos(0), pos(1)), result.upstream().keySet());
        assertEquals(2, result.upstream().values().stream().mapToInt(List::size).sum());
        assertTrue(traceCounts.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void findsDistantSourcesAcrossRegionsWithoutTheirOwnSources() {
        addPath(-6, -6, -5, -4, -3, -2, -1, 0);

        FlowlinePreparation.Result result = prepare(0);

        assertEquals(Set.of(pos(-6), pos(-5), pos(-4), pos(-3), pos(-2), pos(-1), pos(0)), result.upstream().keySet());
        assertEquals(paths.get(pos(-6)), result.upstream().get(pos(-6)));
        assertTrue(traceCounts.values().stream().allMatch(count -> count == 1));
    }

    private FlowlinePreparation.Result prepare(int x) {
        return FlowlinePreparation.prepare(region(pos(x)), tracer);
    }

    private void addPath(int source, int... regions) {
        List<CellPos> cells = new ArrayList<>();
        for (int x : regions) {
            cells.add(pos(x).getMinCell());
        }
        paths.put(pos(source), List.of(new Flowline(cells, cells.get(cells.size() - 1), true)));
        for (int i = 1; i < cells.size(); i++) {
            incoming.computeIfAbsent(cells.get(i), key -> new ArrayList<>()).add(cells.get(i - 1));
        }
    }

    private RegionPos pos(int x) {
        return new RegionPos(x, 0);
    }

    private Region region(RegionPos pos) {
        return new Region(pos, RegionType.INLAND);
    }
}
