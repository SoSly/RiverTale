package org.sosly.rivertale.terrain;

import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OceansTest {

    @Mock
    SampleCache sampleCache;

    @BeforeEach
    void setUp() {
        Store.clear();
        // regionSize=32 cells, cellSize=2 chunks (32 blocks), samplesPerCell=1
        // This gives: 32 cells * 32 blocks = 1024 blocks per region (same as old test)
        CommonConfig.set(new CommonConfig(32, 2, 1, -0.17, 10, 1, 3, 288, false));
    }

    @Test
    void boundariesReturnsEmptyWhenAllOcean() {
        RegionPos region = new RegionPos(0, 0);
        mockSampleCache((x, z) -> true);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, sampleCache);

        assertTrue(boundaries.isEmpty());
    }

    @Test
    void boundariesReturnsEmptyWhenAllLand() {
        RegionPos region = new RegionPos(0, 0);
        mockSampleCache((x, z) -> false);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, sampleCache);

        assertTrue(boundaries.isEmpty());
    }

    @Test
    void boundariesFindsBoundaryAtOceanLandTransition() {
        int cellBlocks = CommonConfig.get().cellBlocks();
        int boundaryCellX = 16;
        int boundaryBlockX = boundaryCellX * cellBlocks;
        RegionPos region = new RegionPos(0, 0);
        mockSampleCache((x, z) -> x >= boundaryBlockX);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, sampleCache);

        assertFalse(boundaries.isEmpty());
        for (OceanBoundary boundary : boundaries) {
            Sample landSample = sampleCache.getOrCompute(boundary.land().getMiddleBlockX(), boundary.land().getMiddleBlockZ());
            Sample oceanSample = sampleCache.getOrCompute(boundary.ocean().getMiddleBlockX(), boundary.ocean().getMiddleBlockZ());
            assertFalse(landSample.isOcean());
            assertTrue(oceanSample.isOcean());
        }
    }

    @Test
    void boundariesFindsBoundaryToNorth() {
        RegionPos region = new RegionPos(0, 0);
        mockSampleCache((x, z) -> z < 0);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, sampleCache);

        assertFalse(boundaries.isEmpty());
        for (OceanBoundary boundary : boundaries) {
            assertTrue(boundary.ocean().z() < boundary.land().z());
        }
    }

    @Test
    void boundariesFindsBoundaryToSouth() {
        int cellBlocks = CommonConfig.get().cellBlocks();
        int boundaryCellZ = 16;
        int boundaryBlockZ = boundaryCellZ * cellBlocks;
        RegionPos region = new RegionPos(0, 0);
        mockSampleCache((x, z) -> z >= boundaryBlockZ);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, sampleCache);

        assertFalse(boundaries.isEmpty());
        for (OceanBoundary boundary : boundaries) {
            assertTrue(boundary.ocean().z() > boundary.land().z());
        }
    }

    @Test
    void boundariesFindsBoundaryToEast() {
        int cellBlocks = CommonConfig.get().cellBlocks();
        int boundaryCellX = 16;
        int boundaryBlockX = boundaryCellX * cellBlocks;
        RegionPos region = new RegionPos(0, 0);
        mockSampleCache((x, z) -> x >= boundaryBlockX);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, sampleCache);

        assertFalse(boundaries.isEmpty());
        for (OceanBoundary boundary : boundaries) {
            assertTrue(boundary.ocean().x() > boundary.land().x());
        }
    }

    @Test
    void boundariesFindsBoundaryToWest() {
        RegionPos region = new RegionPos(0, 0);
        mockSampleCache((x, z) -> x < 0);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, sampleCache);

        assertFalse(boundaries.isEmpty());
        for (OceanBoundary boundary : boundaries) {
            assertTrue(boundary.ocean().x() < boundary.land().x());
        }
    }

    private void mockSampleCache(BiPredicate<Integer, Integer> isOcean) {
        when(sampleCache.getOrCompute(anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int z = invocation.getArgument(1);
            double continents = isOcean.test(x, z) ? -0.5 : 0.5;
            ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
            return new Sample(chunk, continents, 0.0, 0.0, 0.0, 0.0, 0.0);
        });
    }
}
