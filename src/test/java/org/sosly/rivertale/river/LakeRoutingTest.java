package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LakeRoutingTest {
    private final CellPos source = new CellPos(2, 3);
    private final CellPos entry = new CellPos(3, 2);
    private final CellPos pocket = new CellPos(3, 1);
    private final CellPos lip = new CellPos(4, 2);
    private final CellPos outlet = new CellPos(6, 2);
    private final Map<CellPos, Cell> terrain = new HashMap<>();
    private CommonConfig original;
    private RiverWorld world;

    @BeforeEach
    void setUp() {
        original = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, false, false, 0, 0, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        world = new RiverWorld().regions(List.of(new Region(source.getRegion(), RegionType.COASTAL,
            List.of(new Boundary(outlet, new CellPos(7, 2), BoundaryType.OCEAN)))));
        world.cells(pos -> terrain.computeIfAbsent(pos, p -> RiverWorld.land(p, 131, Feature.NONE, List.of())));
        cell(source, 130, FlowDirection.NORTHEAST);
        cell(entry, 119);
        cell(pocket, 121, FlowDirection.SOUTH);
        cell(outlet, 110);
    }

    @AfterEach
    void tearDown() {
        world.close();
        CommonConfig.set(original);
    }

    @Test
    void kettleColorsTheSidePocketWithoutAddingARiverConnection() {
        cell(lip, 123, FlowDirection.EAST);
        CellPos slope = new CellPos(5, 2);
        cell(slope, 122, FlowDirection.EAST);
        RiverRegion region = build();
        assertEquals(List.of(source, entry, lip, slope, outlet), region.flowlines().get(0).cells());
        assertEquals(Feature.KETTLE, region.cells().get(pocket).feature());
        assertEquals(2, region.cells().get(pocket).cell().overFill());
        assertEquals(4, region.cells().get(entry).cell().overFill());
        assertEquals(lip, region.watershedAt(source).downstream(entry));
        assertNull(region.watershedAt(pocket));
        assertTrue(region.cells().get(pocket).cell().cellY().isEmpty());
    }

    @Test
    void closedCoastalLakeCutsThroughToTheOcean() {
        cell(lip, 124, FlowDirection.WEST);
        cell(new CellPos(4, 1), 125, FlowDirection.SOUTHWEST);
        CellPos fifth = new CellPos(5, 1);
        cell(fifth, 128, FlowDirection.WEST);

        RiverRegion region = build();

        assertEquals(List.of(source, entry, lip, fifth, outlet), region.flowlines().get(0).cells());
        assertTrue(region.flowlines().get(0).lakes().isEmpty());
        assertEquals(119, region.watershedAt(source).cellY(lip).orElseThrow());
        assertEquals(119, region.watershedAt(source).cellY(fifth).orElseThrow());
    }

    @Test
    void fifthCellEndsAnInlandWatershedAtAnEndorheicLake() {
        world.regions(List.of(new Region(source.getRegion(), RegionType.INLAND,
            List.of(new Boundary(outlet, new CellPos(7, 2), BoundaryType.BASIN)))));
        cell(lip, 124, FlowDirection.WEST);
        cell(new CellPos(4, 1), 125, FlowDirection.SOUTHWEST);
        CellPos fifth = new CellPos(5, 1);
        cell(fifth, 128, FlowDirection.WEST);
        RiverRegion region = build();
        assertEquals(List.of(source, entry), region.flowlines().get(0).cells());
        assertEquals(entry, region.watershedAt(source).terminus(source).orElseThrow());
        assertEquals(Feature.ENDORHEIC, region.cells().get(entry).feature());
        assertEquals(9, region.cells().get(entry).cell().overFill());
        assertEquals(4, region.cells().values().stream().filter(c -> c.feature() == Feature.ENDORHEIC).count());
        assertFalse(region.watershedAt(source).contains(fifth));
        assertEquals(0, region.cells().get(fifth).cell().overFill());
    }

    @Test
    void consecutiveOneBlockWallsReachTheShoreWithoutFilling() {
        CommonConfig.set(new CommonConfig(8, 1, 1, false, true, 1, 0, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        cell(source, 66, FlowDirection.NORTHEAST);
        cell(entry, 63);
        cell(lip, 64, FlowDirection.WEST);
        CellPos crest = new CellPos(5, 2);
        cell(crest, 65, FlowDirection.EAST, FlowDirection.WEST);
        cell(outlet, 63);
        RiverRegion region = build();
        Flowline path = region.flowlines().get(0);
        assertEquals(List.of(source, entry, lip, crest, outlet), path.cells());
        assertTrue(path.lakes().isEmpty());
        assertEquals(0, region.cells().get(entry).cell().overFill());
    }

    @Test
    void twoBlockSecondWallFallsBackToFillingFromTheOriginalHeight() {
        CommonConfig.set(new CommonConfig(8, 1, 1, false, true, 1, 0, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        cell(source, 70, FlowDirection.NORTHEAST);
        cell(entry, 63);
        cell(lip, 64, FlowDirection.WEST);
        CellPos crest = new CellPos(5, 2);
        cell(crest, 66, FlowDirection.EAST, FlowDirection.WEST);
        cell(outlet, 63);
        RiverRegion region = build();
        assertEquals(outlet, region.watershedAt(source).terminus(source).orElseThrow());
        assertEquals(Feature.KETTLE, region.cells().get(entry).feature());
        assertEquals(3, region.cells().get(entry).cell().overFill());
        assertEquals(2, region.cells().get(lip).cell().overFill());
    }

    @Test
    void retriesTheNextLowestCutWhenTheCheapestOneWouldRevisitTheRiver() {
        Flowline path = cutAroundBlockedBranch(4);

        assertTrue(path.isValid());
        assertEquals(List.of(new CellPos(5, 1), new CellPos(5, 2), lip, new CellPos(3, 2),
            new CellPos(4, 3), new CellPos(5, 3), outlet), path.cells());
        assertTrue(path.lakes().isEmpty());
    }

    @Test
    void failsWithoutPublishingALakeWhenBacktrackingIsDisabled() {
        Flowline path = cutAroundBlockedBranch(0);

        assertFalse(path.isValid());
        assertTrue(path.lakes().isEmpty());
    }

    private Flowline cutAroundBlockedBranch(int backtrackCells) {
        CommonConfig.set(new CommonConfig(8, 1, 1, false, false, 0, 0, 10, 0.25, 1, 0.5,
            SourceConfig.DEFAULT, CacheConfig.DEFAULT, false, 64, 0, backtrackCells, 4));
        CellPos head = new CellPos(5, 1);
        CellPos bend = new CellPos(5, 2);
        CellPos sink = new CellPos(3, 2);
        CellPos cheapDeadEnd = new CellPos(4, 1);
        CellPos alternate = new CellPos(4, 3);
        CellPos descent = new CellPos(5, 3);
        cell(head, 130, FlowDirection.SOUTH);
        cell(bend, 120, FlowDirection.WEST);
        cell(lip, 115, FlowDirection.WEST);
        cell(sink, 90);
        cell(cheapDeadEnd, 100);
        cell(alternate, 110);
        cell(descent, 95);
        cell(outlet, 80);

        return world.prepare(head.getRegion(), head).trace(head);
    }

    private RiverRegion build() {
        FlowlineTracer tracer = world.prepare(source.getRegion(), source);
        return RiverNetwork.assemble(Set.of(source.getRegion()), tracer.data()).get(source.getRegion());
    }

    private void cell(CellPos pos, int y, FlowDirection... flow) {
        terrain.put(pos, RiverWorld.land(pos, y, Feature.RUN, List.of(flow)));
    }
}
