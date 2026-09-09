package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowlineTracerTest {
    private final Map<CellPos, Cell> cells = new HashMap<>();
    private CommonConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, 0, 1, 3, 288, false));
    }

    @AfterEach
    void tearDown() {
        CommonConfig.set(originalConfig);
    }

    @Test
    void followsOrderedFlowToTheLandSideOfAnOceanBoundary() {
        CellPos source = new CellPos(1, 2);
        CellPos terminus = new CellPos(5, 2);
        Flowline path = tracer(List.of(region(terminus, BoundaryType.OCEAN)), 0).trace(source);

        assertTrue(path.isValid());
        assertEquals(List.of(source, new CellPos(2, 2), new CellPos(3, 2), new CellPos(4, 2), terminus), path.cells());
        assertEquals(terminus, path.terminus());
    }

    @Test
    void basinShoreIsAlsoATerminus() {
        CellPos terminus = new CellPos(4, 2);
        Flowline path = tracer(List.of(region(terminus, BoundaryType.BASIN)), 0).trace(new CellPos(1, 2));

        assertTrue(path.isValid());
        assertEquals(terminus, path.terminus());
    }

    @Test
    void farModeTakesTheFirstProgressingFlowRatherThanTheMostDirectStep() {
        CellPos source = new CellPos(1, 1);
        cells.put(source, cell(source, List.of(FlowDirection.SOUTH, FlowDirection.EAST)));
        Flowline path = tracer(List.of(region(new CellPos(5, 3), BoundaryType.OCEAN)), 1).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(1, 2), path.cells().get(1));
    }

    @Test
    void nearModeClosesTheGapEvenWithoutDownhillFlow() {
        CellPos source = new CellPos(1, 2);
        cells.put(source, cell(source, List.of()));
        Flowline path = tracer(List.of(region(new CellPos(4, 2), BoundaryType.OCEAN)), 3).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(2, 2), path.cells().get(1));
    }

    @Test
    void farModeRotatesNonProgressingFlowOneStepTowardTheDestination() {
        CellPos source = new CellPos(1, 2);
        cells.put(source, cell(source, List.of(FlowDirection.NORTH)));
        Flowline path = tracer(List.of(region(new CellPos(6, 2), BoundaryType.OCEAN)), 1).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(2, 1), path.cells().get(1));
    }

    @Test
    void failedNudgeDoesNotFallBackToForcingAnArbitraryDirection() {
        CellPos source = new CellPos(1, 2);
        cells.put(source, cell(source, List.of(FlowDirection.WEST)));
        Flowline path = tracer(List.of(region(new CellPos(6, 2), BoundaryType.OCEAN)), 0).trace(source);

        assertFalse(path.isValid());
        assertNull(path.terminus());
        assertEquals(List.of(source), path.cells());
    }

    @Test
    void noDownhillDirectionsFarFromWaterInvalidatesThePath() {
        CellPos source = new CellPos(1, 2);
        cells.put(source, cell(source, List.of()));
        Flowline path = tracer(List.of(region(new CellPos(6, 2), BoundaryType.OCEAN)), 0).trace(source);

        assertFalse(path.isValid());
        assertEquals(List.of(source), path.cells());
    }

    @Test
    void divideBlocksASelectedStep() {
        CellPos divide = new CellPos(2, 2);
        cells.put(divide, cell(divide, List.of(FlowDirection.EAST)).withFeature(Feature.DIVIDE));
        Flowline path = tracer(List.of(region(new CellPos(5, 2), BoundaryType.OCEAN)), 0).trace(new CellPos(1, 2));

        assertFalse(path.isValid());
        assertFalse(path.cells().contains(divide));
    }

    @Test
    void cyclicFlowCannotProduceALoopingPath() {
        CellPos source = new CellPos(1, 2);
        CellPos next = new CellPos(2, 2);
        cells.put(next, cell(next, List.of(FlowDirection.WEST)));
        Flowline path = tracer(List.of(region(new CellPos(6, 2), BoundaryType.OCEAN)), 0).trace(source);

        assertFalse(path.isValid());
        assertEquals(List.of(source, next), path.cells());
        assertEquals(path.cells().size(), new HashSet<>(path.cells()).size());
    }

    @Test
    void missingDestinationsProduceAnInvalidPath() {
        Flowline path = tracer(List.of(new Region(new RegionPos(0, 0), RegionType.INLAND)), 10)
            .trace(new CellPos(1, 2));

        assertFalse(path.isValid());
        assertNull(path.terminus());
    }

    @Test
    void sourceAlreadyAtTheShoreProducesAValidSingleCellPath() {
        CellPos source = new CellPos(5, 2);
        Flowline path = tracer(List.of(region(source, BoundaryType.OCEAN)), 0).trace(source);

        assertTrue(path.isValid());
        assertEquals(List.of(source), path.cells());
        assertEquals(source, path.terminus());
    }

    @Test
    void crossesIntoAnAllowedNeighborRegion() {
        CellPos terminus = new CellPos(10, 2);
        Flowline path = tracer(List.of(new Region(new RegionPos(0, 0), RegionType.FLUVIAL),
            region(terminus, BoundaryType.OCEAN)), 0).trace(new CellPos(6, 2));

        assertTrue(path.isValid());
        assertEquals(List.of(new CellPos(6, 2), new CellPos(7, 2), new CellPos(8, 2), new CellPos(9, 2), terminus), path.cells());
    }

    @Test
    void cannotCrossAGapOutsideTheWorkingSet() {
        Flowline path = tracer(List.of(new Region(new RegionPos(0, 0), RegionType.FLUVIAL),
            region(new CellPos(18, 2), BoundaryType.OCEAN)), 32).trace(new CellPos(6, 2));

        assertFalse(path.isValid());
        assertEquals(List.of(new CellPos(6, 2), new CellPos(7, 2)), path.cells());
    }

    @Test
    void rejectsASourceOutsideTheWorkingSet() {
        Flowline path = tracer(List.of(region(new CellPos(5, 2), BoundaryType.OCEAN)), 10)
            .trace(new CellPos(-1, 2));

        assertFalse(path.isValid());
    }

    @Test
    void supportsNegativeWorldCoordinates() {
        CellPos terminus = new CellPos(-2, -3);
        Flowline path = tracer(List.of(region(terminus, BoundaryType.OCEAN)), 0).trace(new CellPos(-6, -3));

        assertTrue(path.isValid());
        assertEquals(terminus, path.terminus());
    }

    @Test
    void equalDistanceTargetsDoNotDependOnBoundaryIterationOrder() {
        Boundary west = new Boundary(new CellPos(1, 3), new CellPos(0, 3), BoundaryType.OCEAN);
        Boundary east = new Boundary(new CellPos(5, 3), new CellPos(6, 3), BoundaryType.BASIN);
        Region first = new Region(new RegionPos(0, 0), RegionType.COASTAL, List.of(east, west));
        Region second = new Region(new RegionPos(0, 0), RegionType.COASTAL, List.of(west, east));

        Flowline a = tracer(List.of(first), 10).trace(new CellPos(3, 3));
        Flowline b = tracer(List.of(second), 10).trace(new CellPos(3, 3));

        assertEquals(a, b);
        assertTrue(a.isValid());
        assertEquals(west.land(), a.terminus());
    }

    @Test
    void sharedTracerKeepsEachTraceIndependentIncludingConcurrentRequests() {
        FlowlineTracer tracer = tracer(List.of(region(new CellPos(6, 2), BoundaryType.OCEAN)), 10);
        CellPos source = new CellPos(1, 2);
        Flowline expected = tracer.trace(source);
        tracer.trace(new CellPos(1, 3));
        List<Flowline> paths = IntStream.range(0, 32).parallel().mapToObj(i -> tracer.trace(source)).toList();

        assertTrue(expected.isValid());
        assertTrue(paths.stream().allMatch(expected::equals));
    }

    @Test
    void returnedPathCannotBeChangedThroughItsInputOrAccessor() {
        CellPos source = new CellPos(1, 2);
        List<CellPos> positions = new ArrayList<>(List.of(source));
        Flowline path = new Flowline(positions, source, true);
        positions.clear();

        assertEquals(List.of(source), path.cells());
        assertThrows(UnsupportedOperationException.class, () -> path.cells().clear());
    }

    private FlowlineTracer tracer(List<Region> regions, int threshold) {
        return new FlowlineTracer(regions,
            pos -> cells.getOrDefault(pos, cell(pos, List.of(FlowDirection.EAST))), threshold);
    }

    private Region region(CellPos terminus, BoundaryType type) {
        Boundary boundary = new Boundary(terminus, terminus.relative(FlowDirection.EAST), type);
        return new Region(terminus.getRegion(), RegionType.COASTAL, List.of(boundary));
    }

    private Cell cell(CellPos pos, List<FlowDirection> directions) {
        return new Cell(pos, Set.of()).withFlowDirections(directions);
    }
}
