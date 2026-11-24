package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

class WatershedPreparationTest {
    private CommonConfig originalConfig;
    private MockedStatic<RegionCache> cacheStatic;
    private FlowlineTracer tracer;
    private MockedStatic<SourceTrace> preview;
    private final Map<CellPos, List<CellPos>> incoming = new HashMap<>();
    private final Map<RegionPos, List<Flowline>> paths = new HashMap<>();
    private final Map<RegionPos, Integer> traceCounts = new HashMap<>();
    private final Set<RegionPos> prepared = new HashSet<>();

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, true, true, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        Store.clear();
        RegionCache cache = mock(RegionCache.class);
        when(cache.getOrCompute(any(RegionPos.class))).thenAnswer(call -> region(call.getArgument(0)));
        cacheStatic = mockStatic(RegionCache.class);
        cacheStatic.when(RegionCache::get).thenReturn(cache);
        tracer = mock(FlowlineTracer.class);
        // Like PreparedRiverData, refuse regions that were never prepared.
        when(tracer.region(any(RegionPos.class))).thenAnswer(call -> {
            RegionPos pos = call.getArgument(0);
            if (!prepared.contains(pos)) {
                throw new IllegalStateException("Incomplete river preparation: missing region at " + pos);
            }
            return region(pos);
        });
        // Every step in these scenarios flows toward its destination, so no search ever spends its allowance.
        when(tracer.movesAway(any(CellPos.class), any(CellPos.class))).thenReturn(false);
        when(tracer.awayStepAllowance()).thenReturn(0);
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
    void gathersTributariesJoiningDownstreamAndTheirRemoteSources() {
        Flowline seed = path(0, 0, 8, 0, 9, 0);
        Flowline joining = path(16, 0, 8, 1, 8, 0, 9, 0);
        Flowline remote = path(24, 0, 16, 0, 8, 1, 8, 0, 9, 0);
        Flowline unrelated = path(16, 4, 8, 4, 9, 4);
        List<Flowline> gathered = gather(seed);

        assertEquals(Set.of(seed, joining, remote), new HashSet<>(gathered));
        assertFalse(gathered.contains(unrelated));
        assertTrue(traceCounts.values().stream().allMatch(count -> count == 1));
    }

    @Test
    void revisitsIndexedPathsWhenNewCellsConnectInsideAnAlreadyExaminedRegion() {
        Flowline seed = path(0, 0, 1, 0);
        Flowline joining = path(3, 2, 2, 1, 1, 0);
        Flowline remote = path(5, 2, 4, 2, 3, 2, 2, 1, 1, 0);

        assertEquals(Set.of(seed, joining, remote), new HashSet<>(gather(seed)));
    }

    @Test
    void discoversCrossingsWhoseOtherCornersBelongToDifferentRegions() {
        Flowline seed = path(7, 7, 8, 8, 9, 8);
        Flowline crossing = path(8, 7, 7, 8, 6, 8);

        assertEquals(Set.of(seed, crossing), new HashSet<>(gather(seed)));
    }

    @Test
    void emptyPreparedPathsDoNotExpandTheWorld() {
        assertTrue(WatershedPreparation.gather(new FlowlinePreparation.Result(Map.of(), Map.of(), tracer)).isEmpty());
        assertTrue(traceCounts.isEmpty());
    }

    private List<Flowline> gather(Flowline seed) {
        RegionPos root = seed.cells().get(0).getRegion();
        return WatershedPreparation.gather(new FlowlinePreparation.Result(Map.of(root, List.of(seed)), Map.of(), tracer));
    }

    private Flowline path(int... coordinates) {
        List<CellPos> cells = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2) {
            CellPos target = new CellPos(coordinates[i], coordinates[i + 1]);
            if (cells.isEmpty()) {
                cells.add(target);
                continue;
            }
            CellPos current = cells.get(cells.size() - 1);
            while (!current.equals(target)) {
                current = new CellPos(current.x() + Integer.signum(target.x() - current.x()),
                    current.z() + Integer.signum(target.z() - current.z()));
                cells.add(current);
            }
        }
        cells.forEach(cell -> prepared.add(cell.getRegion()));
        Flowline path = new Flowline(cells, cells.get(cells.size() - 1), true);
        RegionPos origin = cells.get(0).getRegion();
        paths.computeIfAbsent(origin, key -> new ArrayList<>()).add(path);
        for (int i = 1; i < cells.size(); i++) {
            incoming.computeIfAbsent(cells.get(i), key -> new ArrayList<>()).add(cells.get(i - 1));
        }
        return path;
    }

    private Region region(RegionPos pos) {
        return new Region(pos, RegionType.FLUVIAL);
    }
}
