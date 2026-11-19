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
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.client.ClientRegionCache;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.river.PreparedRiverData.CellData;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatershedPreviewEncodingTest {
    private static final RegionPos CENTER = new RegionPos(0, 0);

    private CommonConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, false, false, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT, false));
    }

    @AfterEach
    void tearDown() {
        CommonConfig.set(originalConfig);
    }

    @Test
    void sendsOnlyValidUnmergedFlowlinesInOrder() {
        Flowline first = flowline(0, 0, 1, 0, 2, 0);
        Flowline invalid = new Flowline(List.of(new CellPos(0, 5)), null, false);
        Flowline merged = flowline(3, 4, 4, 4);
        Flowline second = flowline(0, 7, 1, 7);
        Watershed watershed = Watershed.fromLinks(new CellPos(4, 4), Map.of(new CellPos(3, 4), new CellPos(4, 4)));

        ClientRegionCache.Region sent = send(List.of(first, invalid, merged, second), List.of(watershed));

        assertEquals(List.of(first.cells(), second.cells()), sent.paths().stream()
            .map(path -> path.points().stream().map(ClientRegionCache.Point::pos).toList())
            .toList());
    }

    @Test
    void sendsEachFlowlinePointWithItsPreparedHeight() {
        Flowline flowline = flowline(0, 0, 1, 1, 2, 1);

        ClientRegionCache.Region sent = send(List.of(flowline), List.of());

        assertEquals(List.of(point(0, 0), point(1, 1), point(2, 1)), sent.paths().get(0).points());
    }

    @Test
    void sendsWatershedLinksAsEdgesAndTerminusAsOutlet() {
        CellPos source = new CellPos(2, 2);
        CellPos middle = new CellPos(3, 2);
        CellPos terminus = new CellPos(4, 2);
        Watershed watershed = Watershed.fromLinks(terminus, Map.of(source, middle, middle, terminus));

        ClientRegionCache.Region sent = send(List.of(), List.of(watershed));

        assertEquals(Set.of(edge(source, middle), edge(middle, terminus)), new HashSet<>(sent.edges()));
        assertEquals(List.of(point(4, 2)), sent.outlets());
    }

    private ClientRegionCache.Region send(List<Flowline> flowlines, List<Watershed> watersheds) {
        Region region = new Region(CENTER, RegionType.FLUVIAL);
        Map<CellPos, CellData> cells = new HashMap<>();
        for (CellPos pos : region.cells()) {
            cells.put(pos, new CellData(Feature.NONE, List.of(), height(pos)));
        }
        PreparedRiverData data = new PreparedRiverData(Map.of(CENTER, region), cells, Map.of(), Map.of(), Map.of(),
            Map.of(), 0, 63);
        WatershedPreview.Result result = new WatershedPreview.Result(Map.of(CENTER, flowlines), watersheds, data, CENTER);
        return ClientRegionCache.Region.decode(result.encode(CENTER));
    }

    private static int height(CellPos pos) {
        return 100 + pos.x() * 10 + pos.z();
    }

    private static ClientRegionCache.Point point(int x, int z) {
        CellPos pos = new CellPos(x, z);
        return new ClientRegionCache.Point(pos, height(pos));
    }

    private static ClientRegionCache.Edge edge(CellPos from, CellPos to) {
        return new ClientRegionCache.Edge(from, to, height(from), height(to));
    }

    private static Flowline flowline(int... coordinates) {
        List<CellPos> cells = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2) {
            cells.add(new CellPos(coordinates[i], coordinates[i + 1]));
        }
        return new Flowline(cells, cells.get(cells.size() - 1), true);
    }
}
