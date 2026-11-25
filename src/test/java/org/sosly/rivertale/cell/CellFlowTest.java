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
        CommonConfig.set(new CommonConfig(8, 1, 1, false, false, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
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

        // Cold: no neighbor has been computed yet, so classify has to populate the cache itself.
        for (FlowDirection direction : FlowDirection.D8) {
            assertEquals(null, CellCache.get().getIfPresent(center.relative(direction)));
        }
        assertEquals(Feature.SNOWMELT, Feature.classify(cell, null).feature());

        // Warm: the neighbor cache now holds all eight neighbors with flow.
        assertFalse(cell.hasInflowingNeighbor());
        assertEquals(Feature.SNOWMELT, Feature.classify(cell, null).feature());
    }

    @Test
    void springUsesDownhillGradeInsteadOfAbsoluteHeight() {
        CellPos hillside = new CellPos(0, 0);
        provider.defaultHeight = 105;
        provider.put(hillside.relative(FlowDirection.EAST), 103, false);

        assertEquals(Feature.SPRING, Feature.classify(CellCache.get().getOrComputeWithFlow(hillside), null).feature());

        CellPos plateau = new CellPos(10, 0);
        provider.put(plateau, 130, false);
        for (FlowDirection direction : FlowDirection.D8) {
            provider.put(plateau.relative(direction), 129, false);
        }
        assertFalse(new Spring().classify(CellCache.get().getOrComputeWithFlow(plateau), null));
    }

    @Test
    void diagonalSpringOutletNeedsMoreDropThanACardinalOutlet() {
        CellPos center = new CellPos(0, 0);
        CellPos outlet = center.relative(FlowDirection.NORTHEAST);
        provider.put(outlet, 118, false);
        Spring spring = new Spring();
        assertFalse(spring.classify(CellCache.get().getOrComputeWithFlow(center), null));

        provider.put(outlet, 117, false);
        CellCache.get().clear();
        SampleCache.get().clear();
        assertTrue(spring.classify(CellCache.get().getOrComputeWithFlow(center), null));
    }

    @Test
    void springSkipsWaterOutletsAndChecksOtherOutgoingDirections() {
        CellPos center = new CellPos(0, 0);
        provider.put(center.relative(FlowDirection.NORTH), 60, true);
        provider.put(center.relative(FlowDirection.SOUTH), 60, false);
        Spring spring = new Spring();
        assertFalse(spring.classify(CellCache.get().getOrComputeWithFlow(center), null));

        provider.put(center.relative(FlowDirection.EAST), 118, false);
        CellCache.get().clear();
        SampleCache.get().clear();
        assertTrue(spring.classify(CellCache.get().getOrComputeWithFlow(center), null));
    }

    @Test
    void springRejectsAnOtherwiseEligibleCellWithIncomingTerrainFlow() {
        CellPos center = new CellPos(0, 0);
        provider.put(center.relative(FlowDirection.EAST), 118, false);
        provider.put(center.relative(FlowDirection.WEST), 121, false);
        assertFalse(new Spring().classify(CellCache.get().getOrComputeWithFlow(center), null));
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
