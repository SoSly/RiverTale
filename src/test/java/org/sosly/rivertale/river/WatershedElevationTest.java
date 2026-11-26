package org.sosly.rivertale.river;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.PreparedRiverData.CellData;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatershedElevationTest {
    private static final CellPos A = new CellPos(0, 0);
    private static final CellPos B = new CellPos(1, 0);
    private static final CellPos C = new CellPos(2, 0);

    @Test
    void keepsSamplesThatAlreadyDescendToTheOutlet() {
        assertEquals(List.of(76, 70, 65), chainElevations(63, 76, 70, 65));
    }

    @Test
    void lowersOnlyHigherCellsDownstream() {
        assertEquals(List.of(69, 69, 66), chainElevations(63, 69, 74, 66));
        assertEquals(List.of(74, 74, 65), chainElevations(63, 74, 77, 65));
        assertEquals(List.of(66, 66, 66), chainElevations(63, 66, 75, 72));
    }

    @Test
    void propagatesThroughTheConfluenceWithoutLoweringTheOtherTributary() {
        Watershed junction = Watershed.fromLinks(C, Map.of(A, C, B, C));
        PreparedRiverData data = samples(63, 66, 80, 72);

        assertEquals(List.of(66, 80, 66), elevations(junction.withElevations(data, 0)));
        assertEquals(72, data.cell(C).height());
        assertEquals(OptionalInt.empty(), data.cell(C).cell().cellY());
    }

    @Test
    void floorsElevationsAtTheTopWaterBlockForEachSeaLevel() {
        assertEquals(List.of(62, 62, 62), chainElevations(63, 60, 70, 65));
        assertEquals(List.of(69, 69, 69), chainElevations(70, 60, 70, 65));
    }

    @Test
    void raisesNaturallyFlatTerrainOneBlockEveryFourSteps() {
        assertEquals(List.of(66, 66, 66, 66, 67, 67, 67, 67, 68),
            fromOutlet(0.25, 66, 66, 66, 66, 66, 66, 66, 66, 66));
    }

    @Test
    void carriesTheGradeAcrossALongDepressedReachAndIntoLowerTerrain() {
        int[] terrain = new int[42];
        Arrays.fill(terrain, 90);
        terrain[40] = 67;
        terrain[41] = 72;

        List<Integer> expected = IntStream.range(0, terrain.length).map(i -> 67 + i / 4).boxed().toList();
        assertEquals(expected, fromOutlet(0.25, terrain));
    }

    @Test
    void carriesTenthsAcrossEveryTenStepsWithoutEarlyRounding() {
        int[] terrain = new int[21];
        Arrays.fill(terrain, 66);

        List<Integer> expected = IntStream.range(0, terrain.length).map(i -> 66 + i / 10).boxed().toList();
        assertEquals(expected, fromOutlet(0.1, terrain));
    }

    @Test
    void raisesEveryStepForWholeRatiosIncludingTheStepFromTheSea() {
        assertEquals(List.of(64, 66, 68, 70), fromOutlet(2, 60, 60, 60, 60));
    }

    @Test
    void aTributaryInheritsTheConfluenceGradeWithoutAffectingItsSibling() {
        CellPos outlet = new CellPos(3, 0);
        Watershed junction = Watershed.fromLinks(outlet, Map.of(A, C, B, C, C, outlet));
        PreparedRiverData data = samples(63, Map.of(A, sample(A, 66), B, sample(B, 80), C, sample(C, 65), outlet, sample(outlet, 64)));
        Watershed graded = junction.withElevations(data, 2);

        assertEquals(List.of(68, 80, 66), elevations(graded));
        assertEquals(64, graded.cellY(outlet).orElseThrow());
    }

    private List<Integer> fromOutlet(double ratio, int... heights) {
        List<CellPos> positions = IntStream.range(0, heights.length).mapToObj(i -> new CellPos(i, 0)).toList();
        Map<CellPos, CellPos> links = new HashMap<>();
        Map<CellPos, CellData> samples = new HashMap<>();
        for (int i = 0; i < heights.length; i++) {
            CellPos pos = positions.get(i);
            samples.put(pos, sample(pos, heights[i]));
            if (i > 0) {
                links.put(pos, positions.get(i - 1));
            }
        }
        Watershed graded = Watershed.fromLinks(positions.get(0), links).withElevations(samples(63, samples), ratio);
        return positions.stream().map(pos -> graded.cellY(pos).orElseThrow()).toList();
    }

    private List<Integer> chainElevations(int seaLevel, int source, int middle, int outlet) {
        Watershed topology = Watershed.fromLinks(C, Map.of(A, B, B, C));
        return elevations(topology.withElevations(samples(seaLevel, source, middle, outlet), 0));
    }

    private List<Integer> elevations(Watershed watershed) {
        return List.of(watershed.cellY(A).orElseThrow(), watershed.cellY(B).orElseThrow(),
            watershed.cellY(C).orElseThrow());
    }

    private PreparedRiverData samples(int seaLevel, int a, int b, int c) {
        return samples(seaLevel, Map.of(A, sample(A, a), B, sample(B, b), C, sample(C, c)));
    }

    private PreparedRiverData samples(int seaLevel, Map<CellPos, CellData> samples) {
        return new PreparedRiverData(Map.of(), samples, Map.of(), Map.of(), Map.of(), Map.of(), 0, seaLevel);
    }

    private CellData sample(CellPos pos, int height) {
        return new CellData(new Cell(pos, Set.of()), height);
    }
}
