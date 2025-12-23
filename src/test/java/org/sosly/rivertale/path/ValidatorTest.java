package org.sosly.rivertale.path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.RegionType;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidatorTest {

    private static final RegionPos TEST_REGION = new RegionPos(0, 0);
    private static final int CELLS_PER_REGION = 8;

    private static CellPos cell(int row, int col) {
        return CellPos.fromLocal(TEST_REGION, row, col, CELLS_PER_REGION);
    }

    private static Grid emptyGrid() {
        return Grid.create(TEST_REGION, CELLS_PER_REGION);
    }

    @Test
    @DisplayName("Empty paths with no output should remain empty")
    void testEmptyPathsRemainEmpty() {
        Flow input = createEmptyResult();

        Flow flow = Validator.validate(
            input,
            TEST_REGION,
            RegionType.DIVIDE,
            null,
            null);

        assertTrue(flow.riverPaths().isEmpty());
        assertEquals(Direction.NONE, flow.primaryOutputDirection());
    }

    @Test
    @DisplayName("Paths with length >= 3 should be kept without tracing")
    void testLongPathsKeptWithoutTracing() {
        Map<Direction, List<CellPos>> paths = new HashMap<>();
        paths.put(Direction.NORTH, List.of(
            cell(0, 3),
            cell(1, 3),
            cell(2, 3)
        ));

        Crossing[] crossings = new Crossing[4];
        crossings[Direction.NORTH.ordinal()] = new Crossing(0, 3, Crossing.Direction.IN);
        crossings[Direction.SOUTH.ordinal()] = new Crossing(7, 3, Crossing.Direction.OUT);

        Flow input = new Flow(
            emptyGrid(),
            crossings,
            new double[]{1.0, 1.0, 0, 0},
            Direction.SOUTH,
            List.of(),
            paths,
            List.of()
        );

        Flow flow = Validator.validate(
            input,
            TEST_REGION,
            RegionType.DIVIDE,
            null,
            null);

        assertEquals(1, flow.riverPaths().size());
        assertTrue(flow.riverPaths().containsKey(Direction.NORTH));
    }

    @Test
    @DisplayName("Empty path entries should be evicted")
    void testEmptyPathEntriesEvicted() {
        Map<Direction, List<CellPos>> paths = new HashMap<>();
        paths.put(Direction.NORTH, List.of());

        Crossing[] crossings = new Crossing[4];
        crossings[Direction.NORTH.ordinal()] = new Crossing(0, 3, Crossing.Direction.IN);

        Flow input = new Flow(
            emptyGrid(),
            crossings,
            new double[]{1.0, 0, 0, 0},
            Direction.SOUTH,
            List.of(),
            paths,
            List.of()
        );

        Flow flow = Validator.validate(
            input,
            TEST_REGION,
            RegionType.DIVIDE,
            null,
            null);

        assertTrue(flow.riverPaths().isEmpty());
        assertNull(flow.crossings()[Direction.NORTH.ordinal()]);
    }

    @Test
    @DisplayName("Long paths in shore regions should be kept")
    void testLongShorePathsKept() {
        Map<Direction, List<CellPos>> paths = new HashMap<>();
        paths.put(Direction.NORTH, List.of(
            cell(0, 3),
            cell(1, 3),
            cell(2, 3)
        ));

        Crossing[] crossings = new Crossing[4];
        crossings[Direction.NORTH.ordinal()] = new Crossing(0, 3, Crossing.Direction.IN);

        Flow input = new Flow(
            emptyGrid(),
            crossings,
            new double[]{1.0, 0, 0, 0},
            Direction.NONE,
            List.of(cell(2, 3)),
            paths,
            List.of()
        );

        Flow flow = Validator.validate(
            input,
            TEST_REGION,
            RegionType.SHORE,
            null,
            null);

        assertEquals(1, flow.riverPaths().size());
        assertTrue(flow.riverPaths().containsKey(Direction.NORTH));
    }

    @Test
    @DisplayName("Short tributary with confluence should be evicted")
    void testShortTributaryEvicted() {
        Map<Direction, List<CellPos>> paths = new HashMap<>();
        paths.put(Direction.NORTH, List.of(
            cell(0, 3),
            cell(1, 3),
            cell(2, 3),
            cell(3, 3)
        ));
        paths.put(Direction.WEST, List.of(
            cell(3, 0),
            cell(3, 3)
        ));

        Crossing[] crossings = new Crossing[4];
        crossings[Direction.NORTH.ordinal()] = new Crossing(0, 3, Crossing.Direction.IN);
        crossings[Direction.SOUTH.ordinal()] = new Crossing(7, 3, Crossing.Direction.OUT);
        crossings[Direction.WEST.ordinal()] = new Crossing(3, 0, Crossing.Direction.IN);

        List<CellPos> confluences = List.of(cell(3, 3));

        Flow input = new Flow(
            emptyGrid(),
            crossings,
            new double[]{1.0, 1.0, 0, 1.0},
            Direction.SOUTH,
            List.of(),
            paths,
            confluences
        );

        Flow flow = Validator.validate(
            input,
            TEST_REGION,
            RegionType.DIVIDE,
            null,
            null);

        assertEquals(1, flow.riverPaths().size());
        assertTrue(flow.riverPaths().containsKey(Direction.NORTH));
        assertNull(flow.crossings()[Direction.WEST.ordinal()]);
        assertTrue(flow.confluenceCells().isEmpty());
    }

    @Test
    @DisplayName("Long tributary with confluence should be kept")
    void testLongTributaryKept() {
        Map<Direction, List<CellPos>> paths = new HashMap<>();
        paths.put(Direction.NORTH, List.of(
            cell(0, 3),
            cell(1, 3),
            cell(2, 3),
            cell(3, 3)
        ));
        paths.put(Direction.WEST, List.of(
            cell(3, 0),
            cell(3, 1),
            cell(3, 2),
            cell(3, 3)
        ));

        Crossing[] crossings = new Crossing[4];
        crossings[Direction.NORTH.ordinal()] = new Crossing(0, 3, Crossing.Direction.IN);
        crossings[Direction.SOUTH.ordinal()] = new Crossing(7, 3, Crossing.Direction.OUT);
        crossings[Direction.WEST.ordinal()] = new Crossing(3, 0, Crossing.Direction.IN);

        List<CellPos> confluences = List.of(cell(3, 3));

        Flow input = new Flow(
            emptyGrid(),
            crossings,
            new double[]{1.0, 1.0, 0, 1.0},
            Direction.SOUTH,
            List.of(),
            paths,
            confluences
        );

        Flow flow = Validator.validate(
            input,
            TEST_REGION,
            RegionType.DIVIDE,
            null,
            null);

        assertEquals(2, flow.riverPaths().size());
        assertTrue(flow.riverPaths().containsKey(Direction.NORTH));
        assertTrue(flow.riverPaths().containsKey(Direction.WEST));
        assertEquals(1, flow.confluenceCells().size());
    }

    @Test
    @DisplayName("Path without confluence should skip tributary validation")
    void testPathWithoutConfluenceUnchanged() {
        Map<Direction, List<CellPos>> paths = new HashMap<>();
        paths.put(Direction.NORTH, List.of(
            cell(0, 3),
            cell(1, 3),
            cell(2, 3)
        ));

        Crossing[] crossings = new Crossing[4];
        crossings[Direction.NORTH.ordinal()] = new Crossing(0, 3, Crossing.Direction.IN);
        crossings[Direction.SOUTH.ordinal()] = new Crossing(7, 3, Crossing.Direction.OUT);

        Flow input = new Flow(
            emptyGrid(),
            crossings,
            new double[]{1.0, 1.0, 0, 0},
            Direction.SOUTH,
            List.of(),
            paths,
            List.of()
        );

        Flow flow = Validator.validate(
            input,
            TEST_REGION,
            RegionType.DIVIDE,
            null,
            null);

        assertEquals(1, flow.riverPaths().size());
        assertTrue(flow.riverPaths().containsKey(Direction.NORTH));
    }

    @Test
    @DisplayName("Both short tributaries evicted removes confluence")
    void testBothShortTributariesEvictedRemovesConfluence() {
        Map<Direction, List<CellPos>> paths = new HashMap<>();
        paths.put(Direction.NORTH, List.of(
            cell(0, 3),
            cell(1, 3)
        ));
        paths.put(Direction.WEST, List.of(
            cell(1, 0),
            cell(1, 3)
        ));

        Crossing[] crossings = new Crossing[4];
        crossings[Direction.NORTH.ordinal()] = new Crossing(0, 3, Crossing.Direction.IN);
        crossings[Direction.SOUTH.ordinal()] = new Crossing(7, 3, Crossing.Direction.OUT);
        crossings[Direction.WEST.ordinal()] = new Crossing(1, 0, Crossing.Direction.IN);

        List<CellPos> confluences = List.of(cell(1, 3));

        Flow input = new Flow(
            emptyGrid(),
            crossings,
            new double[]{1.0, 1.0, 0, 1.0},
            Direction.SOUTH,
            List.of(),
            paths,
            confluences
        );

        Flow flow = Validator.validate(
            input,
            TEST_REGION,
            RegionType.DIVIDE,
            null,
            null);

        assertTrue(flow.riverPaths().isEmpty());
        assertTrue(flow.confluenceCells().isEmpty());
    }

    private Flow createEmptyResult() {
        return new Flow(
            emptyGrid(),
            new Crossing[4],
            new double[4],
            Direction.NONE,
            List.of(),
            Map.of(),
            List.of()
        );
    }
}
