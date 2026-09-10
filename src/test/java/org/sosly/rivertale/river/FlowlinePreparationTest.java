package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionExplorer;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FlowlinePreparationTest {
    private CommonConfig originalConfig;
    private MockedStatic<RegionCache> cacheStatic;
    private MockedStatic<RegionExplorer> explorer;
    private MockedStatic<FlowlinePreview> preview;
    private final Map<RegionPos, Set<Region>> workingSets = new HashMap<>();
    private final Map<RegionPos, List<Flowline>> paths = new HashMap<>();
    private final Map<RegionPos, Integer> traceCounts = new HashMap<>();

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, 10, 1, 3, 288, true, true, 16));
        Store.clear();
        RegionCache cache = mock(RegionCache.class);
        when(cache.getOrCompute(any(RegionPos.class))).thenAnswer(call -> region(call.getArgument(0)));
        cacheStatic = mockStatic(RegionCache.class);
        cacheStatic.when(RegionCache::get).thenReturn(cache);
        explorer = mockStatic(RegionExplorer.class, CALLS_REAL_METHODS);
        explorer.when(() -> RegionExplorer.discover(any(BlockPos.class))).thenAnswer(call -> {
            RegionPos pos = new RegionPos((BlockPos) call.getArgument(0));
            return workingSets.getOrDefault(pos, Set.of(region(pos)));
        });
        preview = mockStatic(FlowlinePreview.class);
        preview.when(() -> FlowlinePreview.trace(any(Region.class), anySet())).thenAnswer(call -> {
            RegionPos pos = ((Region) call.getArgument(0)).pos();
            traceCounts.merge(pos, 1, Integer::sum);
            return paths.getOrDefault(pos, List.of());
        });
    }

    @AfterEach
    void tearDown() {
        preview.close();
        explorer.close();
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
        workingSets.put(pos(-1), Set.of(region(pos(-1)), region(pos(-2)), region(pos(0))));
        addPath(-3, -3, -2);

        FlowlinePreparation.Result result = prepare(0);

        assertEquals(Set.of(pos(0)), result.upstream().keySet());
        assertTrue(result.traced().containsKey(pos(-1)));
        assertFalse(result.traced().containsKey(pos(-3)));
    }

    @Test
    void rejectedCandidateCanContributeToARegionFoundLater() {
        addPath(-2, -2, -1);
        workingSets.put(pos(-2), Set.of(region(pos(-2)), region(pos(-1)), region(pos(0))));
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
    void candidateBoundsIncludeTheTwoEdgeInlandCorridorAndRespectWorldLimits() {
        List<RegionPos> candidates = RegionExplorer.potentialSources(pos(0));
        assertTrue(candidates.contains(pos(2)));
        assertTrue(candidates.contains(new RegionPos(1, 1)));
        assertFalse(candidates.contains(new RegionPos(2, 1)));
        assertEquals(13, candidates.size());
        RegionPos edge = new RegionPos(30_000_000 / CommonConfig.get().regionBlocks() - 1, 0);
        assertTrue(RegionExplorer.potentialSources(edge).stream().noneMatch(RegionPos::containsOutOfBoundsCells));
    }

    private FlowlinePreparation.Result prepare(int x) {
        return FlowlinePreparation.prepare(region(pos(x)));
    }

    private void addPath(int source, int... regions) {
        List<CellPos> cells = new ArrayList<>();
        Set<Region> permitted = new HashSet<>();
        for (int x : regions) {
            cells.add(pos(x).getMinCell());
            permitted.add(region(pos(x)));
        }
        paths.put(pos(source), List.of(new Flowline(cells, cells.get(cells.size() - 1), true)));
        workingSets.put(pos(source), permitted);
    }

    private RegionPos pos(int x) {
        return new RegionPos(x, 0);
    }

    private Region region(RegionPos pos) {
        return new Region(pos, RegionType.INLAND);
    }
}
