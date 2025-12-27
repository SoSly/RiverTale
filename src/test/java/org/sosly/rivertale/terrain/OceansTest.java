package org.sosly.rivertale.terrain;

import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Cache;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.metric.Store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OceansTest {

    @Mock
    Cache<Cell> cellCache;

    @BeforeEach
    void setUp() {
        Store.clear();
        CommonConfig.set(new CommonConfig(1024, 32, -0.17, 10));
    }

    @Test
    void findsValidBoundaryWithOceanToEast() {
        Cache<Sample> cache = mockSampleCache((x, z) -> x >= 160);

        OceanBoundary boundary = Oceans.nearest(new ChunkPos(0, 0), cache, 16);

        assertValidBoundary(cache, boundary);
    }

    @Test
    void findsValidBoundaryWithOceanToWest() {
        Cache<Sample> cache = mockSampleCache((x, z) -> x < -160);

        OceanBoundary boundary = Oceans.nearest(new ChunkPos(0, 0), cache, 16);

        assertValidBoundary(cache, boundary);
    }

    @Test
    void findsValidBoundaryWithOceanToSouth() {
        Cache<Sample> cache = mockSampleCache((x, z) -> z >= 160);

        OceanBoundary boundary = Oceans.nearest(new ChunkPos(0, 0), cache, 16);

        assertValidBoundary(cache, boundary);
    }

    @Test
    void findsValidBoundaryWithOceanToNorth() {
        Cache<Sample> cache = mockSampleCache((x, z) -> z < -160);

        OceanBoundary boundary = Oceans.nearest(new ChunkPos(0, 0), cache, 16);

        assertValidBoundary(cache, boundary);
    }

    private void assertValidBoundary(Cache<Sample> cache, OceanBoundary boundary) {
        assertFalse(cache.getOrCompute(boundary.land().getMiddleBlockX(), boundary.land().getMiddleBlockZ()).isOcean(),
            "Land chunk should not be ocean");
        assertTrue(cache.getOrCompute(boundary.ocean().getMiddleBlockX(), boundary.ocean().getMiddleBlockZ()).isOcean(),
            "Ocean chunk should be ocean");

        int dx = Math.abs(boundary.ocean().x - boundary.land().x);
        int dz = Math.abs(boundary.ocean().z - boundary.land().z);
        assertTrue(dx <= 1 && dz <= 1 && (dx + dz) >= 1,
            "Chunks should be adjacent: land=" + boundary.land() + " ocean=" + boundary.ocean());
    }

    @Test
    void startsOnOceanFindsAdjacentLand() {
        Cache<Sample> cache = mockSampleCache((x, z) -> x < 16);

        OceanBoundary boundary = Oceans.nearest(new ChunkPos(0, 0), cache, 16);

        assertEquals(0, boundary.ocean().x);
        assertTrue(boundary.land().x >= 0);
    }

    @Test
    void isOceanChunkReturnsTrueForOcean() {
        Cache<Sample> cache = mockSampleCache((x, z) -> true);

        assertTrue(Oceans.isOceanChunk(new ChunkPos(0, 0), cache));
    }

    @Test
    void isOceanChunkReturnsFalseForLand() {
        Cache<Sample> cache = mockSampleCache((x, z) -> false);

        assertFalse(Oceans.isOceanChunk(new ChunkPos(0, 0), cache));
    }

    @Test
    void handlesLargeGridSize() {
        Cache<Sample> cache = mockSampleCache((x, z) -> x >= 512);

        OceanBoundary boundary = Oceans.nearest(new ChunkPos(0, 0), cache, 64);

        assertTrue(boundary.ocean().x > boundary.land().x);
    }

    private Cache<Sample> mockSampleCache(BiPredicate<Integer, Integer> isOcean) {
        return new Cache<>() {
            @Override
            public Sample getOrCompute(int x, int z) {
                double continents = isOcean.test(x, z) ? -0.5 : 0.5;
                return new Sample(new ChunkPos(x >> 4, z >> 4), continents, 0, 0, 0, 0, 0);
            }

            @Override
            public void clear() {
            }
        };
    }

    @Test
    void boundariesReturnsEmptyWhenAllOcean() {
        Cache<Sample> sampleCache = mockSampleCache((x, z) -> true);
        RegionPos region = new RegionPos(0, 0);

        mockCellCache((cellX, cellZ) -> true);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, cellCache, sampleCache);

        assertTrue(boundaries.isEmpty());
    }

    @Test
    void boundariesReturnsEmptyWhenAllLand() {
        Cache<Sample> sampleCache = mockSampleCache((x, z) -> false);
        RegionPos region = new RegionPos(0, 0);

        mockCellCache((cellX, cellZ) -> false);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, cellCache, sampleCache);

        assertTrue(boundaries.isEmpty());
    }

    @Test
    void boundariesFindsBoundaryAtOceanLandTransition() {
        int cellSize = CommonConfig.get().cellSize();
        int boundaryCellX = 16;
        int boundaryBlockX = boundaryCellX * cellSize;

        Cache<Sample> sampleCache = mockSampleCache((x, z) -> x >= boundaryBlockX);
        RegionPos region = new RegionPos(0, 0);

        mockCellCache((cellX, cellZ) -> cellX >= boundaryCellX);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, cellCache, sampleCache);

        assertFalse(boundaries.isEmpty());
        for (OceanBoundary boundary : boundaries) {
            assertFalse(sampleCache.getOrCompute(boundary.land().getMiddleBlockX(), boundary.land().getMiddleBlockZ()).isOcean());
            assertTrue(sampleCache.getOrCompute(boundary.ocean().getMiddleBlockX(), boundary.ocean().getMiddleBlockZ()).isOcean());
        }
    }

    @Test
    void boundariesFindsBoundaryToNorth() {
        Cache<Sample> sampleCache = mockSampleCache((x, z) -> z < 0);
        RegionPos region = new RegionPos(0, 0);

        mockCellCache((cellX, cellZ) -> cellZ < 0);

        Set<OceanBoundary> boundaries = Oceans.boundaries(region, cellCache, sampleCache);

        assertFalse(boundaries.isEmpty());
        for (OceanBoundary boundary : boundaries) {
            assertTrue(boundary.ocean().z < boundary.land().z);
        }
    }

    private void mockCellCache(BiPredicate<Integer, Integer> isOcean) {
        when(cellCache.getOrCompute(anyInt(), anyInt())).thenAnswer(invocation -> {
            int x = invocation.getArgument(0);
            int z = invocation.getArgument(1);
            CellPos pos = new CellPos(x, z);
            double continents = isOcean.test(x, z) ? -0.5 : 0.5;
            ChunkPos chunk = new ChunkPos(pos.getMiddleBlockX() >> 4, pos.getMiddleBlockZ() >> 4);
            Sample sample = new Sample(chunk, continents, 0, 0, 0, 0, 0);
            return new Cell(pos, sample, Feature.DEFAULT, List.of());
        });
    }
}
