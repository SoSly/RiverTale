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
import org.mockito.MockedStatic;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Distribution;
import org.sosly.rivertale.metric.Ratio;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.world.RiverShaping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FlowlineTracerTest {
    private final Map<CellPos, Cell> cells = new HashMap<>();
    private CommonConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        Store.clear();
        CommonConfig.set(new CommonConfig(8, 1, 1, 0, 1, 3, 288, false, true, 16));
    }

    @AfterEach
    void tearDown() {
        CommonConfig.set(originalConfig);
        Store.clear();
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
    void divideRejectsThatBranchAndAllowsAnAlternative() {
        cells.put(new CellPos(1, 2), cell(new CellPos(1, 2), List.of(FlowDirection.EAST, FlowDirection.NORTHEAST)));
        CellPos divide = new CellPos(2, 2);
        cells.put(divide, cell(divide, List.of(FlowDirection.EAST)).withFeature(Feature.DIVIDE));
        Flowline path = tracer(List.of(region(new CellPos(5, 2), BoundaryType.OCEAN)), 0).trace(new CellPos(1, 2));

        assertTrue(path.isValid());
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

    @Test
    void fiveCellFallbackPrefersAvailableFlow() {
        CellPos source = new CellPos(1, 1);
        cells.put(source, cell(source, List.of(FlowDirection.SOUTH)));
        Flowline path = fallbackTracer(new CellPos(5, 4)).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(1, 2), path.cells().get(1));
    }

    @Test
    void outsideFallbackRadiusStillFollowsFlow() {
        CellPos source = new CellPos(1, 1);
        cells.put(source, cell(source, List.of(FlowDirection.SOUTH)));
        Flowline path = fallbackTracer(new CellPos(7, 4)).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(1, 2), path.cells().get(1));
    }

    @Test
    void fiveCellFallbackTriesNudgingBeforeGoingStraight() {
        CellPos source = new CellPos(1, 2);
        cells.put(source, cell(source, List.of(FlowDirection.NORTH)));
        Flowline path = fallbackTracer(new CellPos(5, 2)).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(2, 1), path.cells().get(1));
    }

    @Test
    void fiveCellFallbackEscapesAnEmptyFlowList() {
        CellPos source = new CellPos(1, 2);
        cells.put(source, cell(source, List.of()));
        Flowline path = fallbackTracer(new CellPos(6, 2)).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(2, 2), path.cells().get(1));
    }

    @Test
    void fiveCellFallbackGoesStraightWhenNudgingFails() {
        CellPos source = new CellPos(1, 2);
        cells.put(source, cell(source, List.of(FlowDirection.WEST)));
        Flowline path = fallbackTracer(new CellPos(6, 2)).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(2, 2), path.cells().get(1));
    }

    @Test
    void beyondFiveCellsDoesNotForceAnEscape() {
        CellPos source = new CellPos(1, 2);
        cells.put(source, cell(source, List.of()));
        Flowline path = fallbackTracer(new CellPos(6, 3)).trace(source);

        assertFalse(path.isValid());
        assertEquals(List.of(source), path.cells());
    }

    @Test
    void backtracksSeveralCellsToTheSourceAndDiscardsTheFailedBranch() {
        enableMetrics();
        setNudging(false);
        CellPos source = new CellPos(1, 2);
        CellPos terminus = new CellPos(6, 2);
        cells.put(source, cell(source, List.of(FlowDirection.EAST, FlowDirection.SOUTHEAST)));
        putFlow(2, 2, FlowDirection.EAST);
        putFlow(3, 2, FlowDirection.EAST);
        putFlow(2, 3, FlowDirection.EAST);
        putFlow(3, 3, FlowDirection.EAST);
        putFlow(4, 3, FlowDirection.EAST);
        putFlow(5, 3, FlowDirection.NORTHEAST);

        Flowline path = searchTracer(terminus).trace(source);

        assertTrue(path.isValid());
        assertEquals(List.of(source, new CellPos(2, 3), new CellPos(3, 3), new CellPos(4, 3),
            new CellPos(5, 3), terminus), path.cells());
        assertRatio("success", 1, 1);
        assertRatio("retrySuccess", 1, 1);
        Distribution.Snapshot lengths = Store.getDistribution(FlowlineTracer.class, "lengthBlocks").snapshot();
        assertEquals(1, lengths.count());
        assertEquals(48 + 32 * Math.sqrt(2), lengths.max(), 0.000001);
        assertEquals(1, Store.getTimer(FlowlineTracer.class, "backtracking").count());
    }

    @Test
    void exhaustedBranchesAreReusedAndFailedSearchesAreMeasured() {
        enableMetrics();
        setNudging(false);
        CellPos source = new CellPos(1, 2);
        CellPos sharedDeadEnd = new CellPos(3, 2);
        cells.put(source, cell(source, List.of(FlowDirection.NORTHEAST, FlowDirection.SOUTHEAST)));
        putFlow(2, 1, FlowDirection.SOUTHEAST);
        putFlow(2, 3, FlowDirection.NORTHEAST);
        Map<CellPos, Integer> visits = new HashMap<>();
        FlowlineTracer tracer = new FlowlineTracer(List.of(region(new CellPos(6, 2), BoundaryType.OCEAN)), pos -> {
            visits.merge(pos, 1, Integer::sum);
            return cells.getOrDefault(pos, cell(pos, List.of()));
        }, 0);

        Flowline path = tracer.trace(source);

        assertFalse(path.isValid());
        assertEquals(List.of(source, new CellPos(2, 1), sharedDeadEnd), path.cells());
        assertEquals(2, visits.get(sharedDeadEnd));
        assertRatio("success", 0, 1);
        assertRatio("retrySuccess", 0, 1);
        Ratio hits = Store.getRatio(FlowlineTracer.class, "deadEndHits");
        assertTrue(hits.successes() > 0);
        assertTrue(hits.attempts() > hits.successes());
        assertEquals(1, Store.getTimer(FlowlineTracer.class, "backtracking").count());
    }

    @Test
    void successfulFirstChoiceDoesNotCountAsARetry() {
        enableMetrics();
        Flowline path = tracer(List.of(region(new CellPos(6, 2), BoundaryType.OCEAN)), 0)
            .trace(new CellPos(1, 2));

        assertTrue(path.isValid());
        assertRatio("success", 1, 1);
        assertFalse(Store.ratios().containsKey(FlowlineTracer.class.getName() + "_retrySuccess"));
        assertFalse(Store.timers().containsKey(FlowlineTracer.class.getName() + "_backtracking"));
    }

    @Test
    void withinFiveCellsBacktracksAroundABlockedDirectStep() {
        enableMetrics();
        CellPos source = new CellPos(1, 2);
        cells.put(source, cell(source, List.of(FlowDirection.EAST, FlowDirection.SOUTHEAST)));
        CellPos divide = new CellPos(2, 2);
        cells.put(divide, cell(divide, List.of()).withFeature(Feature.DIVIDE));
        Flowline path = tracer(List.of(region(new CellPos(5, 2), BoundaryType.OCEAN)), 5).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(2, 3), path.cells().get(1));
        assertFalse(path.cells().contains(divide));
        assertRatio("success", 1, 1);
        assertRatio("retrySuccess", 1, 1);
    }

    @Test
    void fiveCellFallbackRunsAfterItsNudgedBranchFails() {
        CellPos source = new CellPos(1, 2);
        CellPos divide = new CellPos(2, 1);
        cells.put(source, cell(source, List.of(FlowDirection.NORTH)));
        cells.put(divide, cell(divide, List.of()).withFeature(Feature.DIVIDE));
        Flowline path = fallbackTracer(new CellPos(6, 2)).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(2, 2), path.cells().get(1));
        assertFalse(path.cells().contains(divide));
    }

    @Test
    void longRoutesHaveNoSearchBudgetOrRecursiveStackLimit() {
        CellPos source = new CellPos(0, 2);
        CellPos terminus = new CellPos(4096, 2);
        List<Region> regions = new ArrayList<>();
        for (int x = 0; x < terminus.getRegion().x(); x++) {
            regions.add(new Region(new RegionPos(x, 0), RegionType.INLAND));
        }
        regions.add(region(terminus, BoundaryType.OCEAN));

        Flowline path = tracer(regions, 0).trace(source);

        assertTrue(path.isValid());
        assertEquals(4097, path.cells().size());
        assertEquals(terminus, path.terminus());
        assertTrue(Store.distributions().isEmpty());
        assertTrue(Store.timers().isEmpty());
        assertTrue(Store.ratios().isEmpty());
    }
    @Test
    void nudgingCanBeDisabledAndEnabledOutsideTheFallbackRadius() {
        CellPos source = new CellPos(1, 2);
        CellPos terminus = new CellPos(12, 2);
        cells.put(source, cell(source, List.of(FlowDirection.NORTH)));
        setNudging(false);

        Flowline disabled = fallbackTracer(terminus).trace(source);

        assertFalse(disabled.isValid());
        assertEquals(List.of(source), disabled.cells());

        setNudging(true);
        Flowline enabled = fallbackTracer(terminus).trace(source);

        assertTrue(enabled.isValid());
        assertEquals(new CellPos(2, 1), enabled.cells().get(1));
    }

    @Test
    void disablingNudgingPreservesTheFiveCellFallback() {
        setNudging(false);
        CellPos source = new CellPos(1, 2);
        cells.put(source, cell(source, List.of(FlowDirection.NORTH)));

        Flowline path = fallbackTracer(new CellPos(6, 2)).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(2, 2), path.cells().get(1));
    }

    @Test
    void disablingNudgingStillPrefersDownhillWithinFiveCells() {
        setNudging(false);
        CellPos source = new CellPos(1, 1);
        cells.put(source, cell(source, List.of(FlowDirection.SOUTH)));

        Flowline path = fallbackTracer(new CellPos(5, 4)).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(1, 2), path.cells().get(1));
    }

    @Test
    void nudgingAllowsExactlySixteenBlocksOfClimb() {
        Flowline path = nudgedClimb(100, 116, 16);

        assertTrue(path.isValid());
        assertEquals(new CellPos(2, 1), path.cells().get(1));
    }

    @Test
    void nudgingRejectsSeventeenBlocksOfClimb() {
        Flowline path = nudgedClimb(100, 117, 16);

        assertFalse(path.isValid());
        assertEquals(List.of(new CellPos(1, 2)), path.cells());
    }

    @Test
    void nudgingLimitCanBeAdjusted() {
        assertFalse(nudgedClimb(100, 112, 8).isValid());
        assertTrue(nudgedClimb(100, 112, 12).isValid());
    }

    @Test
    void zeroNudgingLimitAllowsFlatAndDownhillButRejectsAnyClimb() {
        assertTrue(nudgedClimb(100, 100, 0).isValid());
        assertTrue(nudgedClimb(100, 40, 0).isValid());
        assertFalse(nudgedClimb(100, 101, 0).isValid());
    }

    @Test
    void nudgingLimitDoesNotRestrictTheFiveCellFallback() {
        enableMetrics();
        CellPos source = new CellPos(1, 2);
        cells.put(source, cellAtHeight(100, List.of(FlowDirection.NORTH)));
        cells.put(new CellPos(2, 1), cellAtHeight(300, List.of()));
        cells.put(new CellPos(2, 2), cellAtHeight(300, List.of()));

        for (CellPos terminus : List.of(new CellPos(5, 2), new CellPos(6, 2))) {
            Flowline path = fallbackTracer(terminus).trace(source);

            assertTrue(path.isValid());
            assertEquals(new CellPos(2, 2), path.cells().get(1));
        }
        assertEquals(new Distribution.Snapshot(2, 0, 0, 0, 0),
            Store.getDistribution(FlowlineTracer.class, "nudgingRiseBlocks").snapshot());
        assertEquals(0, Store.getDistribution(FlowlineTracer.class, "uphillNudgeBlocks").snapshot().count());
    }

    @Test
    void riseMetricsSumPositiveNudgesWithoutSubtractingDescents() {
        enableMetrics();
        CellPos source = new CellPos(1, 3);
        CellPos terminus = new CellPos(6, 3);
        cells.put(source, cellAtHeight(100, List.of(FlowDirection.NORTH)));
        cells.put(new CellPos(2, 2), cellAtHeight(112, List.of(FlowDirection.SOUTH)));
        cells.put(new CellPos(2, 3), cellAtHeight(90, List.of(FlowDirection.SOUTH)));
        cells.put(new CellPos(3, 4), cellAtHeight(98, List.of(FlowDirection.EAST)));
        cells.put(new CellPos(4, 4), cellAtHeight(90, List.of(FlowDirection.NORTHEAST)));
        cells.put(new CellPos(5, 3), cellAtHeight(80, List.of(FlowDirection.EAST)));
        cells.put(terminus, cellAtHeight(70, List.of()));
        FlowlineTracer tracer = searchTracer(terminus);

        Flowline path = tracer.trace(source);

        assertTrue(path.isValid());
        assertEquals(List.of(source, new CellPos(2, 2), new CellPos(2, 3), new CellPos(3, 4),
            new CellPos(4, 4), new CellPos(5, 3), terminus), path.cells());
        Distribution.Snapshot totals = Store.getDistribution(FlowlineTracer.class, "nudgingRiseBlocks").snapshot();
        assertEquals(new Distribution.Snapshot(1, 20, 20, 20, 20), totals);
        Distribution.Snapshot steps = Store.getDistribution(FlowlineTracer.class, "uphillNudgeBlocks").snapshot();
        assertEquals(new Distribution.Snapshot(2, 10, 8, 12, 10), steps);

        Flowline downhill = tracer.trace(new CellPos(5, 3));

        assertTrue(downhill.isValid());
        totals = Store.getDistribution(FlowlineTracer.class, "nudgingRiseBlocks").snapshot();
        assertEquals(new Distribution.Snapshot(2, 10, 0, 20, 10), totals);
        assertEquals(steps, Store.getDistribution(FlowlineTracer.class, "uphillNudgeBlocks").snapshot());
        CommonConfig config = CommonConfig.get();
        CommonConfig.set(new CommonConfig(config.regionSize(), config.cellSize(), config.samplesPerCell(),
            config.mergeThreshold(), config.minSlope(), config.minPathLength(), config.embankmentRadius(),
            false, config.enableNudging(), config.nudgingLimit()));
        Store.clear();

        assertEquals(path, searchTracer(terminus).trace(source));
        assertTrue(Store.distributions().isEmpty());
    }

    @Test
    void riseMetricsDiscardBacktrackedClimbsAndEntireFailedRivers() {
        enableMetrics();
        CellPos source = new CellPos(1, 3);
        CellPos terminus = new CellPos(6, 3);
        cells.put(source, cellAtHeight(100, List.of(FlowDirection.NORTH, FlowDirection.SOUTH)));
        cells.put(new CellPos(2, 2), cellAtHeight(112, List.of(FlowDirection.NORTH)));
        cells.put(new CellPos(3, 1), cellAtHeight(120, List.of()));
        cells.put(new CellPos(2, 4), cellAtHeight(107, List.of(FlowDirection.EAST)));
        cells.put(new CellPos(3, 4), cellAtHeight(100, List.of(FlowDirection.EAST)));
        cells.put(new CellPos(4, 4), cellAtHeight(90, List.of(FlowDirection.NORTHEAST)));
        cells.put(new CellPos(5, 3), cellAtHeight(80, List.of(FlowDirection.EAST)));
        FlowlineTracer tracer = searchTracer(terminus);

        Flowline path = tracer.trace(source);

        assertTrue(path.isValid());
        assertEquals(List.of(source, new CellPos(2, 4), new CellPos(3, 4), new CellPos(4, 4),
            new CellPos(5, 3), terminus), path.cells());
        assertRatio("retrySuccess", 1, 1);
        Distribution.Snapshot expected = new Distribution.Snapshot(1, 7, 7, 7, 7);
        assertEquals(expected, Store.getDistribution(FlowlineTracer.class, "nudgingRiseBlocks").snapshot());
        assertEquals(expected, Store.getDistribution(FlowlineTracer.class, "uphillNudgeBlocks").snapshot());
        cells.put(new CellPos(0, 7), cellAtHeight(100, List.of(FlowDirection.NORTH)));
        cells.put(new CellPos(1, 6), cellAtHeight(115, List.of()));

        Flowline failed = tracer.trace(new CellPos(0, 7));

        assertFalse(failed.isValid());
        assertEquals(expected, Store.getDistribution(FlowlineTracer.class, "nudgingRiseBlocks").snapshot());
        assertEquals(expected, Store.getDistribution(FlowlineTracer.class, "uphillNudgeBlocks").snapshot());
    }

    @Test
    void riseMetricsExcludeFlatAndDownhillNudges() {
        enableMetrics();

        assertTrue(nudgedClimb(100, 100, 16).isValid());
        assertTrue(nudgedClimb(100, 40, 16).isValid());
        assertEquals(new Distribution.Snapshot(2, 0, 0, 0, 0),
            Store.getDistribution(FlowlineTracer.class, "nudgingRiseBlocks").snapshot());
        assertEquals(0, Store.getDistribution(FlowlineTracer.class, "uphillNudgeBlocks").snapshot().count());
    }

    @Test
    void nudgeRiseIsRetainedWhenTheSameStepAlsoQualifiesAsDirectFallback() {
        enableMetrics();
        CellPos source = new CellPos(1, 2);
        Cell dividedFlow = cellAtHeight(90, List.of());
        when(dividedFlow.feature()).thenReturn(Feature.DIVIDE);
        cells.put(source, cellAtHeight(100, List.of(FlowDirection.NORTHEAST)));
        cells.put(new CellPos(2, 1), dividedFlow);
        cells.put(new CellPos(2, 2), cellAtHeight(112, List.of()));

        Flowline path = fallbackTracer(new CellPos(6, 2)).trace(source);

        assertTrue(path.isValid());
        assertEquals(new CellPos(2, 2), path.cells().get(1));
        Distribution.Snapshot expected = new Distribution.Snapshot(1, 12, 12, 12, 12);
        assertEquals(expected, Store.getDistribution(FlowlineTracer.class, "nudgingRiseBlocks").snapshot());
        assertEquals(expected, Store.getDistribution(FlowlineTracer.class, "uphillNudgeBlocks").snapshot());
    }

    @Test
    void netFallUsesConfiguredSeaLevelInsteadOfTheSampledShoreHeight() {
        enableMetrics();
        CellPos source = new CellPos(1, 2);
        CellPos terminus = new CellPos(5, 2);
        cells.put(source, cellAtHeight(100, List.of()));
        cells.put(terminus, cellAtHeight(300, List.of()));
        FlowlineTracer tracer = fallbackTracer(terminus);
        try (MockedStatic<RiverShaping> shaping = mockStatic(RiverShaping.class)) {
            shaping.when(RiverShaping::getSeaLevel).thenReturn(63);
            assertTrue(tracer.trace(source).isValid());
            shaping.when(RiverShaping::getSeaLevel).thenReturn(80);
            assertTrue(tracer.trace(source).isValid());
            assertFalse(tracer.trace(new CellPos(-1, 2)).isValid());
        }

        assertEquals(new Distribution.Snapshot(2, 28.5, 20, 37, 28.5),
            Store.getDistribution(FlowlineTracer.class, "netFallBlocks").snapshot());
    }

    @Test
    void fallMetricsCompareTheCompletedPathUsingSeaLevelForTheOutlet() {
        enableMetrics();
        CellPos source = new CellPos(1, 2);
        CellPos terminus = new CellPos(4, 2);
        cells.put(source, cellAtHeight(66, List.of()));
        cells.put(new CellPos(2, 2), cellAtHeight(65, List.of()));
        cells.put(new CellPos(3, 2), cellAtHeight(70, List.of()));
        cells.put(terminus, cellAtHeight(200, List.of()));
        FlowlineTracer tracer = fallbackTracer(terminus);
        try (MockedStatic<RiverShaping> shaping = mockStatic(RiverShaping.class)) {
            shaping.when(RiverShaping::getSeaLevel).thenReturn(63);
            Flowline path = tracer.trace(source);

            assertTrue(path.isValid());
            assertEquals(List.of(source, new CellPos(2, 2), new CellPos(3, 2), terminus), path.cells());
            assertEquals(new Distribution.Snapshot(1, 3, 3, 3, 3),
                Store.getDistribution(FlowlineTracer.class, "netFallBlocks").snapshot());
            assertEquals(new Distribution.Snapshot(3, 8.0 / 3, 0, 7, 1),
                Store.getDistribution(FlowlineTracer.class, "cellFallBlocks").snapshot());
            assertEquals(new Distribution.Snapshot(3, 1, 0, 2, 1),
                Store.getDistribution(FlowlineTracer.class, "carveFlatFallBlocks").snapshot());
            assertEquals(new Distribution.Snapshot(3, 1, 1, 1, 1),
                Store.getDistribution(FlowlineTracer.class, "carveDescendingFallBlocks").snapshot());
            assertEquals(70, cells.get(new CellPos(3, 2)).averageEstimatedTerrainHeight());
            assertEquals(200, cells.get(terminus).averageEstimatedTerrainHeight());
            assertFalse(tracer.trace(new CellPos(-1, 2)).isValid());
            assertTrue(tracer.trace(terminus).isValid());
            assertEquals(3, Store.getDistribution(FlowlineTracer.class, "cellFallBlocks").snapshot().count());
            assertRatio("carveFlatFeasible", 1, 1);
            assertRatio("carveDescendingFeasible", 1, 1);
        }
    }

    @Test
    void infeasibleProfilesDoNotLeakPartialFallsOrInvalidateTheTrace() {
        enableMetrics();
        CellPos source = new CellPos(1, 2);
        CellPos terminus = new CellPos(4, 2);
        cells.put(source, cellAtHeight(100, List.of()));
        cells.put(new CellPos(2, 2), cellAtHeight(64, List.of()));
        cells.put(new CellPos(3, 2), cellAtHeight(90, List.of()));
        cells.put(terminus, cellAtHeight(200, List.of()));
        FlowlineTracer tracer = fallbackTracer(terminus);
        try (MockedStatic<RiverShaping> shaping = mockStatic(RiverShaping.class)) {
            shaping.when(RiverShaping::getSeaLevel).thenReturn(63);
            assertTrue(tracer.trace(source).isValid());
            assertRatio("carveFlatFeasible", 1, 1);
            assertRatio("carveDescendingFeasible", 0, 1);
            assertFalse(Store.distributions().containsKey(FlowlineTracer.class.getName() + "_carveDescendingFallBlocks"));
            assertTrue(tracer.trace(new CellPos(3, 2)).isValid());
        }

        assertRatio("carveDescendingFeasible", 1, 2);
        assertEquals(new Distribution.Snapshot(1, 27, 27, 27, 27),
            Store.getDistribution(FlowlineTracer.class, "carveDescendingFallBlocks").snapshot());
        assertRatio("success", 2, 2);
    }

    private Flowline nudgedClimb(int sourceHeight, int nextHeight, int limit) {
        CommonConfig config = CommonConfig.get();
        CommonConfig.set(new CommonConfig(config.regionSize(), config.cellSize(), config.samplesPerCell(),
            config.mergeThreshold(), config.minSlope(), config.minPathLength(), config.embankmentRadius(),
            config.enableMetrics(), true, limit));
        CellPos source = new CellPos(1, 2);
        cells.put(source, cellAtHeight(sourceHeight, List.of(FlowDirection.NORTH)));
        cells.put(new CellPos(2, 1), cellAtHeight(nextHeight, List.of(FlowDirection.EAST)));
        return fallbackTracer(new CellPos(12, 2)).trace(source);
    }

    private Cell cellAtHeight(int height, List<FlowDirection> directions) {
        Cell cell = mock(Cell.class);
        when(cell.averageEstimatedTerrainHeight()).thenReturn(height);
        when(cell.flowDirections()).thenReturn(directions);
        when(cell.feature()).thenReturn(Feature.NONE);
        return cell;
    }
    @Test
    void lengthCountsCardinalAndDiagonalSegmentsInsteadOfStraightLineDistance() {
        CellPos source = new CellPos(-2, -2);
        CellPos terminus = new CellPos(-1, -1);
        Flowline path = new Flowline(List.of(source, new CellPos(-1, -2), new CellPos(0, -1), terminus), terminus, true);

        assertEquals(128 + 64 * Math.sqrt(2), path.lengthBlocks(64), 0.000001);
        assertEquals(32 + 16 * Math.sqrt(2), path.lengthBlocks(16), 0.000001);
        assertEquals(0, new Flowline(List.of(source), source, true).lengthBlocks(64));
    }

    @Test
    void lengthMetricsIncludeOnlySuccessfulTracesInConfiguredBlockUnits() {
        enableMetrics();
        for (int x = 1; x <= 5; x++) {
            CellPos pos = new CellPos(x, x);
            cells.put(pos, cell(pos, List.of()));
        }
        CellPos terminus = new CellPos(5, 5);
        FlowlineTracer tracer = tracer(List.of(region(terminus, BoundaryType.OCEAN)), 10);
        Flowline cardinal = tracer.trace(new CellPos(1, 5));
        Flowline diagonal = tracer.trace(new CellPos(1, 1));
        Flowline failed = tracer.trace(new CellPos(-1, 5));
        Distribution.Snapshot lengths = Store.getDistribution(FlowlineTracer.class, "lengthBlocks").snapshot();

        assertTrue(cardinal.isValid());
        assertTrue(diagonal.isValid());
        assertFalse(failed.isValid());
        assertEquals(2, lengths.count());
        assertEquals(64, lengths.min(), 0.000001);
        assertEquals(64 * Math.sqrt(2), lengths.max(), 0.000001);
        assertEquals(32 + 32 * Math.sqrt(2), lengths.avg(), 0.000001);
        assertEquals(lengths.avg(), lengths.median(), 0.000001);
    }
    private void setNudging(boolean enabled) {
        CommonConfig config = CommonConfig.get();
        CommonConfig.set(new CommonConfig(config.regionSize(), config.cellSize(), config.samplesPerCell(),
            config.mergeThreshold(), config.minSlope(), config.minPathLength(), config.embankmentRadius(),
            config.enableMetrics(), enabled, config.nudgingLimit()));
    }
    private void enableMetrics() {
        CommonConfig.set(new CommonConfig(8, 1, 1, 0, 1, 3, 288, true, true, 16));
    }

    private void assertRatio(String name, long successes, long attempts) {
        Ratio ratio = Store.getRatio(FlowlineTracer.class, name);
        assertEquals(successes, ratio.successes());
        assertEquals(attempts, ratio.attempts());
    }

    private void putFlow(int x, int z, FlowDirection direction) {
        CellPos pos = new CellPos(x, z);
        cells.put(pos, cell(pos, List.of(direction)));
    }

    private FlowlineTracer searchTracer(CellPos terminus) {
        return new FlowlineTracer(List.of(region(terminus, BoundaryType.OCEAN)),
            pos -> cells.getOrDefault(pos, cell(pos, List.of())), 0);
    }
    private FlowlineTracer fallbackTracer(CellPos terminus) {
        return new FlowlineTracer(List.of(new Region(new RegionPos(0, 0), RegionType.INLAND),
            new Region(new RegionPos(1, 0), RegionType.FLUVIAL), region(terminus, BoundaryType.OCEAN)),
            pos -> cells.getOrDefault(pos, cell(pos, List.of(FlowDirection.EAST))), 5);
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
