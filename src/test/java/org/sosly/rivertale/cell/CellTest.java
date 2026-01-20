package org.sosly.rivertale.cell;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.Sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellTest {

    @Test
    void minimalConstructionCreatesBasicCell() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), 0.5, 0.5)
        );

        Cell cell = new Cell(pos, samples);

        assertEquals(pos, cell.pos());
        assertEquals(1, cell.samples().size());
        assertTrue(cell.flowDirections().isEmpty());
        assertEquals(Feature.NONE, cell.feature());
        assertNull(cell.waypoint());
        assertNull(cell.entryY());
        assertNull(cell.exitY());
        assertNull(cell.width());
        assertNull(cell.depth());
        assertNull(cell.upstreamCount());
        assertNull(cell.downstreamCount());
        assertNull(cell.terminus());
        assertNull(cell.entryT());
        assertNull(cell.exitT());
    }

    @Test
    void averageContinentsComputesMean() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), -0.1, 0.5),
            new ChunkPos(1, 0), new Sample(new ChunkPos(1, 0), -0.2, 0.5),
            new ChunkPos(2, 0), new Sample(new ChunkPos(2, 0), -0.3, 0.5)
        );

        Cell cell = new Cell(pos, samples);

        assertEquals(-0.2, cell.averageContinents(), 0.0001);
    }

    @Test
    void averageDepthComputesMean() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), 0.5, 0.3),
            new ChunkPos(1, 0), new Sample(new ChunkPos(1, 0), 0.5, 0.5),
            new ChunkPos(2, 0), new Sample(new ChunkPos(2, 0), 0.5, 0.7)
        );

        Cell cell = new Cell(pos, samples);

        assertEquals(0.5, cell.averageDepth(), 0.0001);
    }

    @Test
    void averageEstimatedTerrainHeightUsesDepthFormula() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), 0.5, 0.5)
        );

        Cell cell = new Cell(pos, samples);

        assertEquals(142, cell.averageEstimatedTerrainHeight());
    }

    @Test
    void isOceanReturnsTrueWhenAllSamplesAreOcean() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), -0.5, 0.5),
            new ChunkPos(1, 0), new Sample(new ChunkPos(1, 0), -0.5, 0.5),
            new ChunkPos(2, 0), new Sample(new ChunkPos(2, 0), -0.5, 0.5)
        );

        Cell cell = new Cell(pos, samples);

        assertTrue(cell.isOcean());
    }

    @Test
    void isOceanReturnsFalseWhenMixed() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), -0.5, 0.5),
            new ChunkPos(1, 0), new Sample(new ChunkPos(1, 0), 0.5, 0.5)
        );

        Cell cell = new Cell(pos, samples);

        assertFalse(cell.isOcean());
    }

    @Test
    void isBasinReturnsTrueWhenBelowSeaLevelAndNotOcean() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), 0.5, -0.1)
        );

        Cell cell = new Cell(pos, samples);
        int estimatedHeight = cell.averageEstimatedTerrainHeight();

        assertTrue(estimatedHeight < 63);
        assertFalse(cell.isOcean());
        assertTrue(cell.isBasin());
    }

    @Test
    void isBasinReturnsFalseWhenOcean() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), -0.5, -0.1)
        );

        Cell cell = new Cell(pos, samples);

        assertTrue(cell.isOcean());
        assertFalse(cell.isBasin());
    }

    @Test
    void isBasinReturnsFalseWhenAnySampleIsOcean() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), 0.5, -0.1),
            new ChunkPos(1, 0), new Sample(new ChunkPos(1, 0), -0.5, -0.1)
        );

        Cell cell = new Cell(pos, samples);

        assertFalse(cell.isOcean());
        assertFalse(cell.isBasin());
    }

    @Test
    void withFlowDirectionsPreservesOtherFields() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), 0.5, 0.5)
        );
        Cell cell = new Cell(pos, samples)
            .withFeature(Feature.RUN)
            .withElevations(100, 95);

        Cell updated = cell.withFlowDirections(List.of(FlowDirection.NORTH, FlowDirection.EAST));

        assertEquals(List.of(FlowDirection.NORTH, FlowDirection.EAST), updated.flowDirections());
        assertEquals(Feature.RUN, updated.feature());
        assertEquals(100, updated.entryY());
        assertEquals(95, updated.exitY());
    }

    @Test
    void withFeaturePreservesOtherFields() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), 0.5, 0.5)
        );
        Cell cell = new Cell(pos, samples)
            .withFlowDirections(List.of(FlowDirection.SOUTH))
            .withElevations(100, 95);

        Cell updated = cell.withFeature(Feature.CASCADE);

        assertEquals(Feature.CASCADE, updated.feature());
        assertEquals(List.of(FlowDirection.SOUTH), updated.flowDirections());
        assertEquals(100, updated.entryY());
        assertEquals(95, updated.exitY());
    }

    @Test
    void withElevationsPreservesOtherFields() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), 0.5, 0.5)
        );
        Cell cell = new Cell(pos, samples)
            .withFlowDirections(List.of(FlowDirection.WEST))
            .withFeature(Feature.RAPIDS);

        Cell updated = cell.withElevations(80, 70);

        assertEquals(80, updated.entryY());
        assertEquals(70, updated.exitY());
        assertEquals(List.of(FlowDirection.WEST), updated.flowDirections());
        assertEquals(Feature.RAPIDS, updated.feature());
    }

    @Test
    void averageErosionThrowsWhenNoEnrichedSamples() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), 0.5, 0.5)
        );

        Cell cell = new Cell(pos, samples);

        assertThrows(IllegalStateException.class, cell::averageErosion);
    }

    @Test
    void averageErosionComputesMeanForEnrichedSamples() {
        CellPos pos = new CellPos(0, 0);
        Map<ChunkPos, Sample> samples = Map.of(
            new ChunkPos(0, 0), new Sample(new ChunkPos(0, 0), 0.5, 0.5, 0.2, 0.3, 0.4, 0.5),
            new ChunkPos(1, 0), new Sample(new ChunkPos(1, 0), 0.5, 0.5, 0.4, 0.3, 0.4, 0.5)
        );

        Cell cell = new Cell(pos, samples);

        assertEquals(0.3, cell.averageErosion(), 0.0001);
    }

    @Test
    void samplePositionsN1ReturnsCenterOnly() {
        List<CellCache.SampleOffset> positions = CellCache.getSamplePositions(3, 1);

        assertEquals(1, positions.size());
        assertEquals(1, positions.get(0).x());
        assertEquals(1, positions.get(0).z());
    }

    @Test
    void samplePositionsN5ReturnsCenterAndCorners() {
        List<CellCache.SampleOffset> positions = CellCache.getSamplePositions(3, 5);

        assertEquals(5, positions.size());

        Set<String> coords = new HashSet<>();
        for (CellCache.SampleOffset p : positions) {
            coords.add(p.x() + "," + p.z());
        }

        assertTrue(coords.contains("1,1"));
        assertTrue(coords.contains("0,0"));
        assertTrue(coords.contains("2,2"));
        assertTrue(coords.contains("0,2"));
        assertTrue(coords.contains("2,0"));
    }

    @Test
    void samplePositionsN9ReturnsAllPositions() {
        List<CellCache.SampleOffset> positions = CellCache.getSamplePositions(3, 9);

        assertEquals(9, positions.size());

        Set<String> coords = new HashSet<>();
        for (CellCache.SampleOffset p : positions) {
            coords.add(p.x() + "," + p.z());
        }

        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                assertTrue(coords.contains(x + "," + z));
            }
        }
    }

    @Test
    void samplePositionsStartsWithCenter() {
        List<CellCache.SampleOffset> positions = CellCache.getSamplePositions(3, 3);

        assertEquals(1, positions.get(0).x());
        assertEquals(1, positions.get(0).z());
    }

    @Test
    void samplePositionsReturnsRequestedCount() {
        for (int n = 1; n <= 9; n++) {
            List<CellCache.SampleOffset> positions = CellCache.getSamplePositions(3, n);
            assertEquals(n, positions.size());
        }
    }

    @Test
    void samplePositionsHasNoDuplicates() {
        List<CellCache.SampleOffset> positions = CellCache.getSamplePositions(3, 9);

        Set<String> coords = new HashSet<>();
        for (CellCache.SampleOffset p : positions) {
            String key = p.x() + "," + p.z();
            assertTrue(coords.add(key), "Duplicate position found: " + key);
        }
    }
}
