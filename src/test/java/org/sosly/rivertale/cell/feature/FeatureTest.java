package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureTest {

    private TestSampleProvider provider;

    @BeforeEach
    void setUp() {
        CommonConfig.set(new CommonConfig(8, 2, 1, false, false, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        SampleCache.shutdown();
        CellCache.shutdown();
        provider = new TestSampleProvider();
        SampleCache.init(provider);
        CellCache.init();
    }

    @AfterEach
    void tearDown() {
        SampleCache.shutdown();
        CellCache.shutdown();
    }

    @Test
    void classifyReturnsNoneWhenNoSourceHandlerMatches() {
        CellPos pos = new CellPos(10, 20);
        Cell original = cellWith(pos, 73, 0.1);

        Cell classified = Feature.classify(original, null);

        assertEquals(Feature.NONE, classified.feature());
        assertEquals(pos, classified.pos());
        assertEquals(original.samplePositions(), classified.samplePositions());
    }

    @Test
    void raisingOnlyTheHeightOfTheNoMatchCellMakesItASource() {
        Cell cell = cellWith(new CellPos(10, 20), 180, 0.1);

        assertEquals(Feature.SNOWMELT, Feature.classify(cell, null).feature());
    }

    @Test
    void classifyReplacesTheExistingFeature() {
        Cell original = cellWith(new CellPos(5, 5), 73, 0.1).withFeature(Feature.RUN);

        Cell classified = Feature.classify(original, null);

        assertEquals(Feature.NONE, classified.feature());
    }

    @Test
    void classifyPicksTheFirstMatchingHandlerInDeclarationOrder() {
        Cell cell = cellWith(new CellPos(10, 20), 180, 0.1);

        assertTrue(Feature.SNOWMELT.handler.classify(cell, null));
        assertTrue(Feature.SPRING.handler.classify(cell, null));
        assertEquals(Feature.SNOWMELT, Feature.classify(cell, null).feature());
    }

    @Test
    void classifyShortCircuitsOceanCellsBeforeAnyHandlerRuns() {
        Cell cell = cellWith(new CellPos(10, 20), 180, 0.1, true).withFeature(Feature.RUN);

        assertTrue(cell.isOcean());
        // The same cell would be SNOWMELT if it were land.
        assertTrue(Feature.SNOWMELT.handler.classify(cell, null));
        assertEquals(Feature.NONE, Feature.classify(cell, null).feature());
    }

    private Cell cellWith(CellPos pos, int height, double vegetation) {
        return cellWith(pos, height, vegetation, false);
    }

    /**
     * Builds the cell through CellCache so its sample chunks are the ones production would assign
     * for this CellPos under the configured cellSize, then supplies samples for those chunks.
     */
    private Cell cellWith(CellPos pos, int height, double vegetation, boolean ocean) {
        Cell cell = CellCache.get().getOrCompute(pos);
        for (ChunkPos chunk : cell.samplePositions()) {
            // erosion 0.1, ridges 0.1, temperature 0.1: none of these block snowmelt/spring, and erosion allows seep.
            provider.putSample(chunk, new Sample(chunk, ocean ? -0.5 : 0.5, height, ocean, 0.1, 0.1, 0.1, vegetation));
        }
        return CellCache.get().getOrComputeWithFlow(pos);
    }

    private static class TestSampleProvider implements SampleProvider {
        private final java.util.Map<Long, Sample> samples = new java.util.HashMap<>();

        void putSample(ChunkPos pos, Sample sample) {
            samples.put(pos.toLong(), sample);
            SampleCache.get().put(pos, sample);
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
            return 0.0;
        }
    }
}
