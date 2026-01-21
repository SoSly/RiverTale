package org.sosly.rivertale.cell;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellTest {

    private TestSampleProvider provider;

    @BeforeEach
    void setUp() {
        SampleCache.shutdown();
        provider = new TestSampleProvider();
        SampleCache.init(provider);
    }

    @AfterEach
    void tearDown() {
        SampleCache.shutdown();
    }

    @Test
    void minimalConstructionCreatesBasicCell() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        provider.putSample(chunkPos, new Sample(chunkPos, 0.5, 0.5));
        Set<ChunkPos> samplePositions = Set.of(chunkPos);

        CellPos pos = new CellPos(0, 0);
        Cell cell = new Cell(pos, samplePositions);

        assertEquals(pos, cell.pos());
        assertEquals(1, cell.samplePositions().size());
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
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        ChunkPos c2 = new ChunkPos(2, 0);
        provider.putSample(c0, new Sample(c0, -0.1, 0.5));
        provider.putSample(c1, new Sample(c1, -0.2, 0.5));
        provider.putSample(c2, new Sample(c2, -0.3, 0.5));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1, c2);
        Cell cell = new Cell(pos, samplePositions);

        assertEquals(-0.2, cell.averageContinents(), 0.0001);
    }

    @Test
    void averageDepthComputesMean() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        ChunkPos c2 = new ChunkPos(2, 0);
        provider.putSample(c0, new Sample(c0, 0.5, 0.3));
        provider.putSample(c1, new Sample(c1, 0.5, 0.5));
        provider.putSample(c2, new Sample(c2, 0.5, 0.7));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1, c2);
        Cell cell = new Cell(pos, samplePositions);

        assertEquals(0.5, cell.averageDepth(), 0.0001);
    }

    @Test
    void averageEstimatedTerrainHeightUsesDepthFormula() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        provider.putSample(chunkPos, new Sample(chunkPos, 0.5, 0.5));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(chunkPos);
        Cell cell = new Cell(pos, samplePositions);

        assertEquals(142, cell.averageEstimatedTerrainHeight());
    }

    @Test
    void isOceanReturnsTrueWhenAllSamplesAreOcean() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        ChunkPos c2 = new ChunkPos(2, 0);
        provider.putSample(c0, new Sample(c0, -0.5, 0.5));
        provider.putSample(c1, new Sample(c1, -0.5, 0.5));
        provider.putSample(c2, new Sample(c2, -0.5, 0.5));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1, c2);
        Cell cell = new Cell(pos, samplePositions);

        assertTrue(cell.isOcean());
    }

    @Test
    void isOceanReturnsFalseWhenMixed() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        provider.putSample(c0, new Sample(c0, -0.5, 0.5));
        provider.putSample(c1, new Sample(c1, 0.5, 0.5));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1);
        Cell cell = new Cell(pos, samplePositions);

        assertFalse(cell.isOcean());
    }

    @Test
    void isBasinReturnsTrueWhenBelowSeaLevelAndNotOcean() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        provider.putSample(chunkPos, new Sample(chunkPos, 0.5, -0.1));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(chunkPos);
        Cell cell = new Cell(pos, samplePositions);
        int estimatedHeight = cell.averageEstimatedTerrainHeight();

        assertTrue(estimatedHeight < 63);
        assertFalse(cell.isOcean());
        assertTrue(cell.isBasin());
    }

    @Test
    void isBasinReturnsFalseWhenOcean() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        provider.putSample(chunkPos, new Sample(chunkPos, -0.5, -0.1));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(chunkPos);
        Cell cell = new Cell(pos, samplePositions);

        assertTrue(cell.isOcean());
        assertFalse(cell.isBasin());
    }

    @Test
    void isBasinReturnsFalseWhenAnySampleIsOcean() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        provider.putSample(c0, new Sample(c0, 0.5, -0.1));
        provider.putSample(c1, new Sample(c1, -0.5, -0.1));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1);
        Cell cell = new Cell(pos, samplePositions);

        assertFalse(cell.isOcean());
        assertFalse(cell.isBasin());
    }

    @Test
    void withFlowDirectionsPreservesOtherFields() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        provider.putSample(chunkPos, new Sample(chunkPos, 0.5, 0.5));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(chunkPos);
        Cell cell = new Cell(pos, samplePositions)
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
        ChunkPos chunkPos = new ChunkPos(0, 0);
        provider.putSample(chunkPos, new Sample(chunkPos, 0.5, 0.5));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(chunkPos);
        Cell cell = new Cell(pos, samplePositions)
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
        ChunkPos chunkPos = new ChunkPos(0, 0);
        provider.putSample(chunkPos, new Sample(chunkPos, 0.5, 0.5));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(chunkPos);
        Cell cell = new Cell(pos, samplePositions)
            .withFlowDirections(List.of(FlowDirection.WEST))
            .withFeature(Feature.RAPIDS);

        Cell updated = cell.withElevations(80, 70);

        assertEquals(80, updated.entryY());
        assertEquals(70, updated.exitY());
        assertEquals(List.of(FlowDirection.WEST), updated.flowDirections());
        assertEquals(Feature.RAPIDS, updated.feature());
    }

    @Test
    void averageErosionEnrichesSamplesLazily() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        provider.putSample(c0, new Sample(c0, 0.5, 0.5));
        provider.putSample(c1, new Sample(c1, 0.5, 0.5));
        provider.setFieldValue(DensityField.EROSION, 0.3);

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1);
        Cell cell = new Cell(pos, samplePositions);

        assertEquals(0.3, cell.averageErosion(), 0.0001);
    }

    @Test
    void averageErosionUsesEnrichedSamplesIfPresent() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        provider.putSample(c0, new Sample(c0, 0.5, 0.5, 0.2, 0.3, 0.4, 0.5));
        provider.putSample(c1, new Sample(c1, 0.5, 0.5, 0.4, 0.3, 0.4, 0.5));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1);
        Cell cell = new Cell(pos, samplePositions);

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

    private static class TestSampleProvider implements SampleProvider {
        private final java.util.Map<Long, Sample> samples = new java.util.HashMap<>();
        private final java.util.Map<DensityField, Double> fieldValues = new java.util.EnumMap<>(DensityField.class);

        void putSample(ChunkPos pos, Sample sample) {
            samples.put(pos.toLong(), sample);
            SampleCache.get().put(pos, sample);
        }

        void setFieldValue(DensityField field, double value) {
            fieldValues.put(field, value);
        }

        @Override
        public Sample sampleCore(ChunkPos pos) {
            Sample cached = samples.get(pos.toLong());
            if (cached != null) {
                return cached;
            }
            return new Sample(pos, 0.0, 0.0);
        }

        @Override
        public double sample(DensityField field, ChunkPos pos) {
            return fieldValues.getOrDefault(field, 0.0);
        }
    }
}
