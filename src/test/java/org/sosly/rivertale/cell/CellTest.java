package org.sosly.rivertale.cell;

import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void averageContinentsComputesMean() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        ChunkPos c2 = new ChunkPos(2, 0);
        provider.putSample(c0, new Sample(c0, -0.1, 142, false));
        provider.putSample(c1, new Sample(c1, -0.2, 142, true));
        provider.putSample(c2, new Sample(c2, -0.3, 142, true));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1, c2);
        Cell cell = new Cell(pos, samplePositions);

        assertEquals(-0.2, cell.averageContinents(), 0.0001);
    }

    @Test
    void averageEstimatedTerrainHeightAveragesNativeHeights() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        ChunkPos c2 = new ChunkPos(2, 0);
        provider.putSample(c0, new Sample(c0, 0.5, 113, false));
        provider.putSample(c1, new Sample(c1, 0.5, 142, false));
        provider.putSample(c2, new Sample(c2, 0.5, 171, false));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1, c2);
        Cell cell = new Cell(pos, samplePositions);

        assertEquals(142, cell.averageEstimatedTerrainHeight());
    }

    @Test
    void isOceanReturnsTrueWhenAllSamplesAreOcean() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        ChunkPos c2 = new ChunkPos(2, 0);
        provider.putSample(c0, new Sample(c0, -0.5, 142, true));
        provider.putSample(c1, new Sample(c1, -0.5, 142, true));
        provider.putSample(c2, new Sample(c2, -0.5, 142, true));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1, c2);
        Cell cell = new Cell(pos, samplePositions);

        assertTrue(cell.isOcean());
    }

    @Test
    void isOceanReturnsFalseWhenMixed() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        provider.putSample(c0, new Sample(c0, -0.5, 142, true));
        provider.putSample(c1, new Sample(c1, 0.5, 142, false));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1);
        Cell cell = new Cell(pos, samplePositions);

        assertFalse(cell.isOcean());
    }

    @Test
    void isBasinReturnsTrueWhenBelowSeaLevelAndNotOcean() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        provider.putSample(chunkPos, new Sample(chunkPos, 0.5, 56, false));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(chunkPos);
        Cell cell = new Cell(pos, samplePositions);

        assertFalse(cell.isOcean());
        assertTrue(cell.isBasin());
    }

    @Test
    void isBasinReturnsFalseWhenOcean() {
        ChunkPos chunkPos = new ChunkPos(0, 0);
        provider.putSample(chunkPos, new Sample(chunkPos, -0.5, 56, true));

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
        provider.putSample(c0, new Sample(c0, 0.5, 56, false));
        provider.putSample(c1, new Sample(c1, -0.5, 56, true));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1);
        Cell cell = new Cell(pos, samplePositions);

        assertFalse(cell.isOcean());
        assertFalse(cell.isBasin());
    }

    @Test
    void averageErosionEnrichesSamplesLazily() {
        ChunkPos c0 = new ChunkPos(0, 0);
        ChunkPos c1 = new ChunkPos(1, 0);
        provider.putSample(c0, new Sample(c0, 0.5, 142, false));
        provider.putSample(c1, new Sample(c1, 0.5, 142, false));
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
        provider.putSample(c0, new Sample(c0, 0.5, 142, false, 0.2, 0.3, 0.4, 0.5));
        provider.putSample(c1, new Sample(c1, 0.5, 142, false, 0.4, 0.3, 0.4, 0.5));

        CellPos pos = new CellPos(0, 0);
        Set<ChunkPos> samplePositions = Set.of(c0, c1);
        Cell cell = new Cell(pos, samplePositions);

        assertEquals(0.3, cell.averageErosion(), 0.0001);
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
            return new Sample(pos, 0.0, 70, false);
        }

        @Override
        public double sample(DensityField field, ChunkPos pos) {
            return fieldValues.getOrDefault(field, 0.0);
        }
    }
}
