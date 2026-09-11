package org.sosly.rivertale.river;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
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
    private final Region first = new Region(new RegionPos(0, 0), RegionType.COASTAL,
        List.of(new Boundary(firstShore, firstShore.relative(FlowDirection.SOUTH), BoundaryType.OCEAN)));
    private final Region middle = new Region(new RegionPos(1, 0), RegionType.FLUVIAL);
    private final Region last = new Region(new RegionPos(1, -1), RegionType.COASTAL,
        List.of(new Boundary(secondShore, secondShore.relative(FlowDirection.NORTH), BoundaryType.OCEAN)));
    private final Map<CellPos, Cell> cells = new HashMap<>();
    private final AtomicInteger routingLoads = new AtomicInteger();
    private CommonConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, 0, 1, 3, 288, true, false, 12));
        Store.clear();
    }

    @AfterEach
    void tearDown() {
        CommonConfig.set(originalConfig);
        Store.clear();
    }

    @Test
    void rejectsACellLoopAndBacktracksToTheNextCandidate() {
        addLoopWithExit();
        Flowline path = tracer().trace(new CellPos(7, 2));

        assertTrue(path.isValid());
        assertEquals(List.of(new CellPos(7, 2), new CellPos(8, 3), new CellPos(9, 2),
            new CellPos(9, 1), new CellPos(9, 0), secondShore), path.cells());
        assertEquals(path.cells().size(), new HashSet<>(path.cells()).size());
        assertEquals(1, Store.getRatio(FlowlineTracer.class, "retrySuccess").successes());
        assertTrue(path.cells().stream().anyMatch(cell -> last.pos().contains(cell)));
    }

    @Test
    void aLoopWithoutAnExitFailsWithoutPoisoningOtherSourceSearches() {
        flow(7, 2, FlowDirection.SOUTHEAST);
        flow(8, 3, FlowDirection.NORTHWEST);
        FlowlineTracer tracer = tracer();

        Flowline firstAttempt = tracer.trace(new CellPos(7, 2));
        Flowline secondAttempt = tracer.trace(new CellPos(8, 3));

        assertFalse(firstAttempt.isValid());
        assertFalse(secondAttempt.isValid());
        assertEquals(List.of(new CellPos(7, 2), new CellPos(8, 3)), firstAttempt.cells());
        assertEquals(List.of(new CellPos(8, 3), new CellPos(7, 2)), secondAttempt.cells());
    }

    @Test
    void revisitsRegionsAtDifferentCellsOnTheWayToTheCoast() {
        flow(7, 3, FlowDirection.SOUTHEAST);
        flow(8, 4, FlowDirection.SOUTHWEST);
        flow(7, 5, FlowDirection.SOUTHEAST);
        flow(8, 6, FlowDirection.SOUTHWEST);

        Flowline path = tracer().trace(new CellPos(7, 3));

        assertTrue(path.isValid());
        assertEquals(firstShore, path.terminus());
        assertEquals(List.of(first.pos(), middle.pos(), first.pos(), middle.pos(), first.pos()),
            path.cells().stream().map(CellPos::getRegion).toList());
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

    private void addLoopWithExit() {
        flow(7, 2, FlowDirection.SOUTHEAST);
        flow(8, 3, FlowDirection.NORTHWEST, FlowDirection.NORTHEAST);
        flow(9, 2, FlowDirection.NORTH);
        flow(9, 1, FlowDirection.NORTH);
        flow(9, 0, FlowDirection.NORTHWEST);
    }

    private void flow(int x, int z, FlowDirection... directions) {
        CellPos pos = new CellPos(x, z);
        cells.put(pos, new Cell(pos, Set.of()).withFlowDirections(List.of(directions)));
    }

    private FlowlineTracer tracer() {
        return new FlowlineTracer(this::regions, pos -> cells.getOrDefault(pos, new Cell(pos, Set.of())), 0, 0);
    }

    private Collection<Region> regions(RegionPos pos) {
        routingLoads.incrementAndGet();
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
