package org.sosly.rivertale.cell;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.cell.feature.Spring;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;
import org.sosly.rivertale.region.RegionTypeCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CellFlowTest {
    private TestSampleProvider provider;
    private CommonConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, 10, 1, 3, 288, false, false, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT, 5000, false));
        SampleCache.shutdown();
        CellCache.shutdown();
        RegionTypeCache.shutdown();
        provider = new TestSampleProvider();
        SampleCache.init(provider);
        CellCache.init();
    }

    @AfterEach
    void tearDown() {
        CellCache.shutdown();
        SampleCache.shutdown();
        CommonConfig.set(originalConfig);
    }

    @Test
    void inlandFlowUsesNativeHeightOrderingWithoutRegionDiscovery() {
        CellPos center = new CellPos(1000, -1000);
        provider.put(center, 100, false);
        provider.put(center.relative(FlowDirection.NORTH), 90, false);
        provider.put(center.relative(FlowDirection.EAST), 60, false);
        provider.put(center.relative(FlowDirection.SOUTH), 100, false);
        provider.put(center.relative(FlowDirection.WEST), 110, false);

        Cell cell = CellCache.get().getOrComputeWithFlow(center);

        assertEquals(List.of(FlowDirection.EAST, FlowDirection.NORTH), cell.flowDirections());
        assertEquals(100, cell.averageEstimatedTerrainHeight());
    }

    @Test
    void oceanCellDoesNotFlowOrBecomeASource() {
        CellPos center = new CellPos(0, 0);
        provider.put(center, 200, true);

        Cell cell = CellCache.get().getOrComputeWithFlow(center);

        assertTrue(cell.isOcean());
        assertTrue(cell.flowDirections().isEmpty());
        assertEquals(Feature.NONE, Feature.classify(cell, null).feature());
    }

    @Test
    void inflowIsDetectedBeforeNeighborFlowHasBeenRequested() {
        CellPos center = new CellPos(0, 0);
        provider.put(center, 180, false);
        provider.put(center.relative(FlowDirection.NORTH), 190, false);
        Cell cell = CellCache.get().getOrCompute(center);

        assertTrue(cell.hasInflowingNeighbor());
        assertEquals(Feature.NONE, Feature.classify(cell, null).feature());
        assertTrue(provider.requested.stream().allMatch(pos -> Math.abs(pos.x) <= 2 && Math.abs(pos.z) <= 2));
    }

    @Test
    void classificationIsIndependentOfNeighborVisitOrder() {
        CellPos center = new CellPos(0, 0);
        provider.put(center, 180, false);
        provider.put(center.relative(FlowDirection.NORTH), 190, false);
        Feature coldResult = Feature.classify(CellCache.get().getOrCompute(center), null).feature();

        CellCache.get().clear();
        SampleCache.get().clear();
        for (FlowDirection direction : FlowDirection.D8) {
            CellCache.get().getOrComputeWithFlow(center.relative(direction));
        }
        Feature warmResult = Feature.classify(CellCache.get().getOrCompute(center), null).feature();

        assertEquals(Feature.NONE, coldResult);
        assertEquals(coldResult, warmResult);
    }

    @Test
    void coldHighPointIsSnowmeltWithColdOrWarmNeighborCache() {
        CellPos center = new CellPos(0, 0);
        provider.put(center, 180, false);
        Cell cell = CellCache.get().getOrCompute(center);

        assertFalse(cell.hasInflowingNeighbor());
        assertEquals(Feature.SNOWMELT, Feature.classify(cell, null).feature());
        assertEquals(Feature.SNOWMELT, Feature.classify(cell, null).feature());
    }

    @Test
    void springThresholdUsesNativeHeightAbove113() {
        CellPos center = new CellPos(0, 0);
        provider.defaultHeight = 100;
        provider.put(center, 113, false);
        Spring spring = new Spring();

        assertFalse(spring.classify(CellCache.get().getOrCompute(center), null));
        provider.put(center, 114, false);
        CellCache.get().clear();
        SampleCache.get().clear();
        assertTrue(spring.classify(CellCache.get().getOrCompute(center), null));
        assertEquals(Feature.SPRING, Feature.classify(CellCache.get().getOrCompute(center), null).feature());
    }

    private static class TestSampleProvider implements SampleProvider {
        private final Map<Long, Sample> samples = new HashMap<>();
        private final Set<ChunkPos> requested = new HashSet<>();
        private int defaultHeight = 120;

        void put(CellPos pos, int height, boolean ocean) {
            ChunkPos chunk = new ChunkPos(pos.x(), pos.z());
            samples.put(chunk.toLong(), new Sample(chunk, 0.5, height, ocean, 0.5, 0.1, 0.1, 0.1));
        }

        @Override
        public Sample sampleCore(ChunkPos pos) {
            requested.add(pos);
            return samples.getOrDefault(pos.toLong(), new Sample(pos, 0.5, defaultHeight, false, 0.5, 0.1, 0.1, 0.1));
        }

        @Override
        public double sample(DensityField field, ChunkPos pos) {
            throw new AssertionError("Fixture already supplies all terrain fields: " + field);
        }
    }
}
