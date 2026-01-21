package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureTest {

    private TestSampleProvider provider;

    @BeforeEach
    void setUp() {
        CommonConfig.set(new CommonConfig(8, 2, 1, -0.17, 10, 1, 3, 288, false));
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
    void classifyReturnsNoneWhenNoHandlerMatches() {
        CellPos pos = new CellPos(10, 20);
        ChunkPos chunkPos = new ChunkPos(0, 0);
        Sample sample = new Sample(chunkPos, 0.5, 0.1, 0.5, 0.8, 0.5, 0.1);
        provider.putSample(chunkPos, sample);
        Set<ChunkPos> samplePositions = Set.of(chunkPos);
        int y = sample.estimatedTerrainHeight();
        Cell original = new Cell(pos, samplePositions, List.of(FlowDirection.SOUTH), Feature.NONE, null, y, y, null, null, null, null, null, null, null);

        Cell cell = Feature.classify(original, null);

        assertEquals(Feature.NONE, cell.feature());
        assertEquals(pos, cell.pos());
        assertEquals(samplePositions, cell.samplePositions());
    }

    @Test
    void classifyPreservesExistingFeature() {
        CellPos pos = new CellPos(5, 5);
        ChunkPos chunkPos = new ChunkPos(0, 0);
        Sample sample = new Sample(chunkPos, 0.5, 0.1, 0.5, 0.8, 0.5, 0.1);
        provider.putSample(chunkPos, sample);
        Set<ChunkPos> samplePositions = Set.of(chunkPos);
        int y = sample.estimatedTerrainHeight();
        Cell original = new Cell(pos, samplePositions, List.of(FlowDirection.SOUTH), Feature.RUN, null, y, y, null, null, null, null, null, null, null);

        Cell classified = Feature.classify(original, null);

        assertEquals(Feature.NONE, classified.feature());
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
            return new Sample(pos, 0.0, 0.0);
        }

        @Override
        public double sample(DensityField field, ChunkPos pos) {
            return 0.0;
        }
    }
}
