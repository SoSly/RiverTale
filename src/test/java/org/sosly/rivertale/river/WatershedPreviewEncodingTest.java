package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
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
        CommonConfig.set(new CommonConfig(8, 1, 1, false, false, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
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

    @Test
    void sendsAssignedElevationsForRiverBordersPathsAndMarkers() {
        CellPos source = new CellPos(2, 2);
        CellPos terminus = new CellPos(3, 2);
        CellPos other = new CellPos(1, 2);
        Map<CellPos, CellData> samples = Map.of(
            source, new CellData(Feature.NONE, List.of(), height(source)),
            terminus, new CellData(Feature.NONE, List.of(), height(terminus)));
        PreparedRiverData data = new PreparedRiverData(Map.of(), samples, Map.of(), Map.of(), Map.of(), Map.of(), 0, 63);
        Watershed watershed = Watershed.fromLinks(terminus, Map.of(source, terminus)).withElevations(data, 0.25);

        ClientRegionCache.Region sent = send(List.of(flowline(1, 2, 2, 2, 3, 2)), List.of(watershed));

        int assignedY = height(source);
        ClientRegionCache.Point outlet = new ClientRegionCache.Point(terminus, assignedY);
        ClientRegionCache.Cell river = cell(sent, terminus);
        ClientRegionCache.Cell nonRiver = cell(sent, other);
        assertEquals(assignedY, river.y());
        assertEquals(OptionalInt.of(height(terminus)), river.averageY());
        assertEquals(OptionalInt.of(assignedY), cell(sent, source).averageY());
        assertEquals(height(other), nonRiver.y());
        assertEquals(OptionalInt.empty(), nonRiver.averageY());
        assertEquals(List.of(new ClientRegionCache.Edge(source, terminus, assignedY, assignedY)), sent.edges());
        assertEquals(List.of(outlet), sent.outlets());
        assertEquals(List.of(point(1, 2), point(2, 2), outlet), sent.paths().get(0).points());
    }

    @Test
    void sendsSourceLabelHeightsOnlyForSourcesInAnElevatedWatershed() {
        CellPos source = new CellPos(2, 2);
        CellPos terminus = new CellPos(3, 2);
        CellPos outside = new CellPos(4, 2);
        Map<CellPos, CellData> samples = Map.of(
            source, new CellData(Feature.SNOWMELT, List.of(), height(source)),
            terminus, new CellData(Feature.NONE, List.of(), height(terminus)));
        PreparedRiverData data = new PreparedRiverData(Map.of(), samples, Map.of(), Map.of(), Map.of(), Map.of(), 0, 63);
        Watershed watershed = Watershed.fromLinks(terminus, Map.of(source, terminus)).withElevations(data, 0.25);

        ClientRegionCache.Region sent = send(List.of(), List.of(watershed),
            Map.of(source, Feature.SNOWMELT, outside, Feature.SNOWMELT));

        assertEquals(new ClientRegionCache.Cell(source, Feature.SNOWMELT, height(source), OptionalInt.of(height(source)),
            OptionalDouble.empty()), cell(sent, source));
        assertEquals(OptionalInt.empty(), cell(sent, outside).averageY());
    }

    @Test
    void sendsFractionalAndZeroFlowOnlyForWatershedCells() {
        CellPos source = new CellPos(2, 2);
        CellPos terminus = new CellPos(3, 2);
        CellPos outside = new CellPos(4, 2);
        Watershed watershed = Watershed.fromLinks(terminus, Map.of(source, terminus)).withFlowAccumulation(0.0, 0.5);

        ClientRegionCache.Region sent = send(List.of(), List.of(watershed));

        assertEquals(OptionalDouble.of(0.0), cell(sent, source).flow());
        assertEquals(OptionalDouble.of(0.5), cell(sent, terminus).flow());
        assertEquals(OptionalDouble.empty(), cell(sent, outside).flow());
    }

    private ClientRegionCache.Cell cell(ClientRegionCache.Region region, CellPos pos) {
        return region.cells().stream().filter(cell -> cell.pos().equals(pos)).findFirst().orElseThrow();
    }

    private ClientRegionCache.Region send(List<Flowline> flowlines, List<Watershed> watersheds) {
        return send(flowlines, watersheds, Map.of());
    }

    private ClientRegionCache.Region send(List<Flowline> flowlines, List<Watershed> watersheds,
                                          Map<CellPos, Feature> features) {
        Region region = new Region(CENTER, RegionType.FLUVIAL);
        Map<CellPos, CellData> cells = new HashMap<>();
        for (CellPos pos : region.cells()) {
            cells.put(pos, new CellData(features.getOrDefault(pos, Feature.NONE), List.of(), height(pos)));
        }
        RiverRegion result = new RiverRegion(region, cells, flowlines, watersheds);
        return ClientRegionCache.Region.decode(result.encode());
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
