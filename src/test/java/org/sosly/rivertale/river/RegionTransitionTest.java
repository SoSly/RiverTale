package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTransitionTest {
    private final CellPos source = new CellPos(7, 3);
    private final CellPos entry = new CellPos(8, 3);
    private final CellPos outlet = new CellPos(9, 3);
    private final Map<CellPos, Cell> cells = new HashMap<>();
    private CommonConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        configure(false);
    }

    @AfterEach
    void tearDown() {
        CommonConfig.set(originalConfig);
    }

    @Test
    void naturalCrossingsAndReverseDiscoveryOnlyFollowTheRegionalOrder() {
        for (RegionType from : RegionType.values()) {
            for (RegionType to : RegionType.values()) {
                Region origin = region(new CellPos(7, 7), from);
                Region destination = region(outlet, to);
                flow(source, FlowDirection.EAST);
                flow(entry, FlowDirection.EAST);
                FlowlineTracer tracer = tracer(List.of(origin, destination), 0);
                boolean expected = (from == RegionType.INLAND && to == RegionType.FLUVIAL)
                    || (from == RegionType.FLUVIAL && to == RegionType.COASTAL);
                String crossing = from + " -> " + to;

                assertEquals(expected, tracer.trace(source).isValid(), crossing);
                assertEquals(expected, tracer.predecessors(entry).contains(source), crossing);
                UpstreamSearch upstream = new UpstreamSearch(tracer);
                Set<RegionPos> found = upstream.expand(List.of(outlet));
                assertEquals(expected, found.contains(source.getRegion()), crossing);
            }
        }
    }

    @Test
    void nudgingCannotEnterAnUpstreamRegionEvenWhenTheStepApproachesTheOutlet() {
        configure(true);
        CellPos nudge = new CellPos(8, 2);
        Region origin = region(new CellPos(7, 0), RegionType.COASTAL);
        Region neighbor = new Region(entry.getRegion(), RegionType.FLUVIAL);
        flow(source, FlowDirection.EAST);
        FlowlineTracer tracer = tracer(List.of(origin, neighbor), 0);

        assertFalse(tracer.predecessors(nudge).contains(source));
        assertEquals(List.of(source), tracer.trace(source).cells());
    }

    @Test
    void fallbackKeepsCoastalRiversInsideTheirRegion() {
        Region origin = region(new CellPos(7, 7), RegionType.COASTAL);
        Region neighbor = region(outlet, RegionType.FLUVIAL);
        Flowline path = tracer(List.of(origin, neighbor), 20).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(7, 7), path.terminus());
        assertTrue(path.cells().stream().allMatch(pos -> pos.getRegion().equals(source.getRegion())));
    }

    @Test
    void inlandRiversCanFollowFluvialTerrainToCoastalDestinations() {
        List<CellPos> expected = new ArrayList<>();
        for (int x = 6; x <= 18; x++) {
            CellPos pos = new CellPos(x, 3);
            expected.add(pos);
            flow(pos, FlowDirection.EAST);
        }
        List<Region> regions = List.of(
            new Region(new RegionPos(0, 0), RegionType.INLAND),
            new Region(new RegionPos(1, 0), RegionType.FLUVIAL),
            region(new CellPos(18, 3), RegionType.COASTAL));
        FlowlineTracer tracer = tracer(regions, 0);

        assertEquals(expected, tracer.trace(expected.get(0)).cells());
        assertTrue(tracer.trace(expected.get(0)).isValid());
        UpstreamSearch upstream = new UpstreamSearch(tracer);
        assertEquals(Set.of(new RegionPos(0, 0), new RegionPos(1, 0), new RegionPos(2, 0)),
            upstream.expand(List.of(new CellPos(18, 3))));
    }

    @Test
    void inlandWithoutFluvialCanStillEndInItsOwnBasin() {
        CellPos basin = new CellPos(7, 5);
        flow(source, FlowDirection.SOUTH);
        flow(new CellPos(7, 4), FlowDirection.SOUTH);
        Region origin = region(basin, RegionType.INLAND);
        Region neighbor = region(outlet, RegionType.INLAND);
        Flowline path = tracer(List.of(origin, neighbor), 0).trace(source);

        assertTrue(path.isValid());
        assertEquals(List.of(source, new CellPos(7, 4), basin), path.cells());
    }

    @Test
    void inlandCannotSkipFluvialToReachCoastalTerrain() {
        Region origin = new Region(source.getRegion(), RegionType.INLAND);
        Region destination = region(outlet, RegionType.COASTAL);
        flow(source, FlowDirection.EAST);
        flow(entry, FlowDirection.EAST);
        FlowlineTracer tracer = tracer(List.of(origin, destination), 20);

        assertFalse(tracer.hasDestinations(origin.pos()));
        assertFalse(tracer.trace(source).isValid());
        assertFalse(tracer.predecessors(entry).contains(source));
    }

    @Test
    void cornerNeighborsDoNotAllowDiagonalRegionalHandoffs() {
        CellPos corner = new CellPos(7, 7);
        CellPos diagonal = new CellPos(8, 8);
        CellPos shore = new CellPos(9, 9);
        flow(corner, FlowDirection.SOUTHEAST);
        flow(diagonal, FlowDirection.SOUTHEAST);
        List<Region> regions = List.of(new Region(corner.getRegion(), RegionType.FLUVIAL),
            region(shore, RegionType.COASTAL));

        assertFalse(tracer(regions, 20).trace(corner).isValid());
    }

    @Test
    void upstreamBoundaryWaterRemainsForbiddenInsideTheCurrentRegion() {
        CellPos approaching = new CellPos(6, 3);
        Region current = region(new CellPos(7, 5), RegionType.COASTAL);
        Region upstream = new Region(entry.getRegion(), RegionType.FLUVIAL,
            List.of(new Boundary(entry, source, BoundaryType.BASIN)));
        flow(approaching, FlowDirection.EAST);
        flow(source, FlowDirection.SOUTH);
        flow(new CellPos(7, 4), FlowDirection.SOUTH);
        FlowlineTracer tracer = tracer(List.of(current, upstream), 20);

        assertFalse(tracer.predecessors(source).contains(approaching));
        Flowline path = tracer.trace(approaching);
        assertTrue(path.isValid());
        assertFalse(path.cells().contains(source));
    }

    private void configure(boolean nudging) {
        CommonConfig.set(new CommonConfig(8, 1, 1, 0, 1, 3, 288, false, nudging, 12,
            5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT, 5000, false));
    }

    private Region region(CellPos terminus, RegionType type) {
        BoundaryType boundaryType = type == RegionType.COASTAL ? BoundaryType.OCEAN : BoundaryType.BASIN;
        return new Region(terminus.getRegion(), type, List.of(
            new Boundary(terminus, terminus.relative(FlowDirection.SOUTH), boundaryType)));
    }

    private void flow(CellPos pos, FlowDirection direction) {
        cells.put(pos, new Cell(pos, Set.of()).withFlowDirections(List.of(direction)));
    }

    private FlowlineTracer tracer(List<Region> regions, int fallback) {
        return new FlowlineTracer(regions, pos -> cells.getOrDefault(pos, new Cell(pos, Set.of())), fallback, 10);
    }
}
