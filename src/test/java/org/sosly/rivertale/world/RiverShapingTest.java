package org.sosly.rivertale.world;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionExplorer;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.Basins;
import org.sosly.rivertale.terrain.Oceans;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RiverShapingTest {

    @Mock
    CellCache cellCache;

    @Mock
    RegionCache regionCache;

    private MockedStatic<CellCache> cellCacheStatic;
    private MockedStatic<RegionCache> regionCacheStatic;
    private MockedStatic<RegionExplorer> regionExplorerStatic;
    private MockedStatic<Oceans> oceansStatic;
    private MockedStatic<Basins> basinsStatic;

    @BeforeEach
    void setUp() {
        Store.clear();
        CommonConfig.set(new CommonConfig(8, 2, 1, -0.17, 10, 1, 3, 288, false));

        cellCacheStatic = mockStatic(CellCache.class);
        regionCacheStatic = mockStatic(RegionCache.class);
        regionExplorerStatic = mockStatic(RegionExplorer.class);
        oceansStatic = mockStatic(Oceans.class);
        basinsStatic = mockStatic(Basins.class);

        cellCacheStatic.when(CellCache::get).thenReturn(cellCache);
        regionCacheStatic.when(RegionCache::get).thenReturn(regionCache);
    }

    @AfterEach
    void tearDown() {
        cellCacheStatic.close();
        regionCacheStatic.close();
        regionExplorerStatic.close();
        oceansStatic.close();
        basinsStatic.close();
    }

    @Test
    void emptyWorkingSetSkipsBoundaryDetection() {
        ChunkAccess chunk = mockChunk(0, 0);
        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of());

        RiverShaping.shape(chunk);

        oceansStatic.verify(() -> Oceans.boundaries(any(), any()), never());
        basinsStatic.verify(() -> Basins.boundaries(any(), any()), never());
    }

    @Test
    void coastalRegionGetsBothOceanAndBasinDetection() {
        RegionPos pos = new RegionPos(0, 0);
        Region coastal = new Region(pos, RegionType.COASTAL);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(coastal));

        Boundary oceanBoundary = new Boundary(List.of(new CellPos(0, 0)), BoundaryType.OCEAN);
        Boundary basinBoundary = new Boundary(List.of(new CellPos(1, 1)), BoundaryType.BASIN);
        oceansStatic.when(() -> Oceans.boundaries(pos, cellCache)).thenReturn(oceanBoundary);
        basinsStatic.when(() -> Basins.boundaries(pos, cellCache)).thenReturn(basinBoundary);

        RiverShaping.shape(chunk);

        oceansStatic.verify(() -> Oceans.boundaries(pos, cellCache));
        basinsStatic.verify(() -> Basins.boundaries(pos, cellCache));
    }

    @Test
    void fluvialRegionGetsOnlyBasinDetection() {
        RegionPos pos = new RegionPos(0, 0);
        Region fluvial = new Region(pos, RegionType.FLUVIAL);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(fluvial));

        Boundary basinBoundary = new Boundary(List.of(new CellPos(1, 1)), BoundaryType.BASIN);
        basinsStatic.when(() -> Basins.boundaries(pos, cellCache)).thenReturn(basinBoundary);

        RiverShaping.shape(chunk);

        oceansStatic.verify(() -> Oceans.boundaries(any(), any()), never());
        basinsStatic.verify(() -> Basins.boundaries(pos, cellCache));
    }

    @Test
    void oceanicRegionGetsNoBoundaryDetection() {
        RegionPos pos = new RegionPos(0, 0);
        Region oceanic = new Region(pos, RegionType.OCEANIC);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(oceanic));

        RiverShaping.shape(chunk);

        oceansStatic.verify(() -> Oceans.boundaries(any(), any()), never());
        basinsStatic.verify(() -> Basins.boundaries(any(), any()), never());
    }

    @Test
    void inlandRegionGetsNoBoundaryDetection() {
        RegionPos pos = new RegionPos(0, 0);
        Region inland = new Region(pos, RegionType.INLAND);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(inland));

        RiverShaping.shape(chunk);

        oceansStatic.verify(() -> Oceans.boundaries(any(), any()), never());
        basinsStatic.verify(() -> Basins.boundaries(any(), any()), never());
    }

    @Test
    void mixedWorkingSetCallsCorrectDetectors() {
        RegionPos coastalPos = new RegionPos(0, 0);
        RegionPos fluvialPos = new RegionPos(1, 0);
        Region coastal = new Region(coastalPos, RegionType.COASTAL);
        Region fluvial = new Region(fluvialPos, RegionType.FLUVIAL);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(coastal, fluvial));

        Boundary oceanBoundary = new Boundary(List.of(new CellPos(0, 0)), BoundaryType.OCEAN);
        oceansStatic.when(() -> Oceans.boundaries(coastalPos, cellCache)).thenReturn(oceanBoundary);
        basinsStatic.when(() -> Basins.boundaries(any(), any())).thenReturn(null);

        RiverShaping.shape(chunk);

        oceansStatic.verify(() -> Oceans.boundaries(coastalPos, cellCache));
        oceansStatic.verify(() -> Oceans.boundaries(fluvialPos, cellCache), never());
        basinsStatic.verify(() -> Basins.boundaries(coastalPos, cellCache));
        basinsStatic.verify(() -> Basins.boundaries(fluvialPos, cellCache));
    }

    @Test
    void enrichedRegionsStoredInCache() {
        RegionPos pos = new RegionPos(0, 0);
        Region coastal = new Region(pos, RegionType.COASTAL);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(coastal));

        Boundary oceanBoundary = new Boundary(List.of(new CellPos(0, 0)), BoundaryType.OCEAN);
        oceansStatic.when(() -> Oceans.boundaries(pos, cellCache)).thenReturn(oceanBoundary);
        basinsStatic.when(() -> Basins.boundaries(pos, cellCache)).thenReturn(null);

        RiverShaping.shape(chunk);

        verify(regionCache).put(any(Region.class));
    }

    private ChunkAccess mockChunk(int chunkX, int chunkZ) {
        ChunkAccess chunk = mock(ChunkAccess.class);
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        when(chunk.getPos()).thenReturn(chunkPos);
        return chunk;
    }
}
