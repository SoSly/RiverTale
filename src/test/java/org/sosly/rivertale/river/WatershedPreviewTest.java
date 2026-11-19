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
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatershedPreviewTest {
    private CommonConfig originalConfig;
    private MockedStatic<SourceTrace> preview;
    private FlowlineTracer tracer;
    private final Set<RegionPos> visible = new HashSet<>();
    private final Map<CellPos, List<CellPos>> incoming = new HashMap<>();
    private final Map<RegionPos, List<Flowline>> paths = new HashMap<>();
    private final Map<RegionPos, Integer> traceCounts = new HashMap<>();

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        configure(true);
        Store.clear();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                visible.add(new RegionPos(x, z));
            }
        }
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
        Store.clear();
        CommonConfig.set(originalConfig);
    }

    @Test
    void localModeBuildsVisibleRiversWithoutDiscoveringRemoteSourcesOrClippingOutlets() {
        Flowline root = path(0, 0, 8, 0, 16, 0);
        Flowline neighbor = path(-8, 0, 0, 0, 8, 0, 16, 0);
        path(-24, 0, -16, 0, -8, 0, 0, 0, 8, 0, 16, 0);
        path(16, 8, 16, 0);

        WatershedPreview.Result result = prepare();

        assertEquals(visible, result.paths().keySet());
        assertEquals(visible, traceCounts.keySet());
        assertTrue(traceCounts.values().stream().allMatch(count -> count == 1));
        assertEquals(List.of(root), result.paths().get(new RegionPos(0, 0)));
        assertEquals(List.of(neighbor), result.paths().get(new RegionPos(-1, 0)));
        assertEquals(1, result.watersheds().size());
        Watershed watershed = result.watersheds().get(0);
        Set<CellPos> cells = new HashSet<>(watershed.outletToSources());
        assertEquals(25, cells.size());
        for (int x = -8; x <= 16; x++) {
            assertTrue(cells.contains(new CellPos(x, 0)));
        }
        assertEquals(new CellPos(16, 0), watershed.terminus(new CellPos(-8, 0)).orElseThrow());
        verify(tracer, never()).predecessors(any(CellPos.class));
    }

    @Test
    void normalModeStillIncludesRemoteUpstreamsAndDownstreamTributaries() {
        configure(false);
        path(0, 0, 8, 0, 16, 0);
        path(-8, 0, 0, 0, 8, 0, 16, 0);
        path(-24, 0, -16, 0, -8, 0, 0, 0, 8, 0, 16, 0);
        path(16, 8, 16, 0);

        WatershedPreview.Result result = prepare();

        assertEquals(visible, result.paths().keySet());
        assertTrue(traceCounts.containsKey(new RegionPos(-3, 0)));
        assertTrue(traceCounts.containsKey(new RegionPos(2, 1)));
        assertEquals(1, result.watersheds().size());
        Set<CellPos> cells = new HashSet<>(result.watersheds().get(0).outletToSources());
        assertEquals(49, cells.size());
        for (int x = -24; x <= 16; x++) {
            assertTrue(cells.contains(new CellPos(x, 0)));
        }
        for (int z = 1; z <= 8; z++) {
            assertTrue(cells.contains(new CellPos(16, z)));
        }
    }

    @Test
    void emptyLocalViewDoesNotDiscoverAdditionalRegions() {
        WatershedPreview.Result result = prepare();

        assertEquals(visible, result.paths().keySet());
        assertTrue(result.paths().values().stream().allMatch(List::isEmpty));
        assertTrue(result.watersheds().isEmpty());
        assertEquals(visible, traceCounts.keySet());
        verify(tracer, never()).predecessors(any(CellPos.class));
    }

    @Test
    void fullPreviewReturnsPathsForEveryVisibleRegion() {
        configure(false);
        RegionPos center = new RegionPos(0, 0);
        PreparedRiverData data = snapshot();
        try (MockedStatic<RiverPreparation> preparation = mockStatic(RiverPreparation.class)) {
            preparation.when(() -> RiverPreparation.prepare(eq(center), any(), eq(false))).thenReturn(data);

            WatershedPreview.Result result = WatershedPreview.prepare(center, visible);

            assertEquals(visible, result.paths().keySet());
            assertTrue(traceCounts.keySet().containsAll(visible));
        }
    }

    private PreparedRiverData snapshot() {
        Map<RegionPos, Region> regions = new HashMap<>();
        Map<CellPos, List<CellPos>> incomingCells = new HashMap<>();
        for (RegionPos pos : visible) {
            Region region = region(pos);
            regions.put(pos, region);
            region.cells().forEach(cell -> incomingCells.put(cell, List.of()));
        }
        return new PreparedRiverData(regions, Map.of(), Map.of(), Map.of(), incomingCells, Map.of(), 0, 63);
    }

    private WatershedPreview.Result prepare() {
        return WatershedPreview.tracePrepared(new RegionPos(0, 0), visible, tracer);
    }

    private void configure(boolean local) {
        CommonConfig.set(new CommonConfig(8, 1, 1, true, true, 12, 5, 0,
            SourceConfig.DEFAULT, CacheConfig.DEFAULT, local));
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
        Flowline path = new Flowline(cells, cells.get(cells.size() - 1), true);
        paths.computeIfAbsent(cells.get(0).getRegion(), key -> new ArrayList<>()).add(path);
        for (int i = 1; i < cells.size(); i++) {
            incoming.computeIfAbsent(cells.get(i), key -> new ArrayList<>()).add(cells.get(i - 1));
        }
        return path;
    }

    private Region region(RegionPos pos) {
        return new Region(pos, RegionType.FLUVIAL);
    }
}
