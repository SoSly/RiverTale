package org.sosly.rivertale.worldgen.river;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiverPathValidatorTest {

    @Test
    @DisplayName("Empty paths with no output should remain empty")
    void testEmptyPathsRemainEmpty() {
        D8FlowResult input = createEmptyResult();

        D8FlowResult result = RiverPathValidator.validate(
            input,
            new RiverRegionKey(0, 0),
            RegionFeatureType.DIVIDE,
            null,
            null);

        assertTrue(result.riverPaths().isEmpty());
        assertEquals(PathDirection.NONE, result.primaryOutputDirection());
    }

    @Test
    @DisplayName("Paths with length >= 3 should be kept without tracing")
    void testLongPathsKeptWithoutTracing() {
        Map<PathDirection, List<int[]>> paths = new HashMap<>();
        paths.put(PathDirection.NORTH, List.of(
            new int[]{0, 3},
            new int[]{1, 3},
            new int[]{2, 3}
        ));

        EdgeCrossing[] crossings = new EdgeCrossing[4];
        crossings[PathDirection.NORTH.ordinal()] = new EdgeCrossing(0, 3, EdgeCrossing.Direction.IN);
        crossings[PathDirection.SOUTH.ordinal()] = new EdgeCrossing(7, 3, EdgeCrossing.Direction.OUT);

        D8FlowResult input = new D8FlowResult(
            new FlowDirection[8][8],
            crossings,
            new double[]{1.0, 1.0, 0, 0},
            PathDirection.SOUTH,
            new int[0][],
            paths,
            List.of()
        );

        D8FlowResult result = RiverPathValidator.validate(
            input,
            new RiverRegionKey(0, 0),
            RegionFeatureType.DIVIDE,
            null,
            null);

        assertEquals(1, result.riverPaths().size());
        assertTrue(result.riverPaths().containsKey(PathDirection.NORTH));
    }

    @Test
    @DisplayName("Empty path entries should be evicted")
    void testEmptyPathEntriesEvicted() {
        Map<PathDirection, List<int[]>> paths = new HashMap<>();
        paths.put(PathDirection.NORTH, List.of());

        EdgeCrossing[] crossings = new EdgeCrossing[4];
        crossings[PathDirection.NORTH.ordinal()] = new EdgeCrossing(0, 3, EdgeCrossing.Direction.IN);

        D8FlowResult input = new D8FlowResult(
            new FlowDirection[8][8],
            crossings,
            new double[]{1.0, 0, 0, 0},
            PathDirection.SOUTH,
            new int[0][],
            paths,
            List.of()
        );

        D8FlowResult result = RiverPathValidator.validate(
            input,
            new RiverRegionKey(0, 0),
            RegionFeatureType.DIVIDE,
            null,
            null);

        assertTrue(result.riverPaths().isEmpty());
        assertNull(result.crossings()[PathDirection.NORTH.ordinal()]);
    }

    @Test
    @DisplayName("Long paths in shore regions should be kept")
    void testLongShorePathsKept() {
        Map<PathDirection, List<int[]>> paths = new HashMap<>();
        paths.put(PathDirection.NORTH, List.of(
            new int[]{0, 3},
            new int[]{1, 3},
            new int[]{2, 3}
        ));

        EdgeCrossing[] crossings = new EdgeCrossing[4];
        crossings[PathDirection.NORTH.ordinal()] = new EdgeCrossing(0, 3, EdgeCrossing.Direction.IN);

        D8FlowResult input = new D8FlowResult(
            new FlowDirection[8][8],
            crossings,
            new double[]{1.0, 0, 0, 0},
            PathDirection.NONE,
            new int[][]{{2, 3}},
            paths,
            List.of()
        );

        D8FlowResult result = RiverPathValidator.validate(
            input,
            new RiverRegionKey(0, 0),
            RegionFeatureType.SHORE,
            null,
            null);

        assertEquals(1, result.riverPaths().size());
        assertTrue(result.riverPaths().containsKey(PathDirection.NORTH));
    }

    private D8FlowResult createEmptyResult() {
        return new D8FlowResult(
            new FlowDirection[8][8],
            new EdgeCrossing[4],
            new double[4],
            PathDirection.NONE,
            new int[0][],
            Map.of(),
            List.of()
        );
    }
}
