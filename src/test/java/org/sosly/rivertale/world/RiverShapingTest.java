package org.sosly.rivertale.world;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
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
import org.sosly.rivertale.terrain.Coastal;
import org.sosly.rivertale.terrain.Fluvial;

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

    @Mock
    RandomState randomState;

    private MockedStatic<CellCache> cellCacheStatic;
    private MockedStatic<RegionCache> regionCacheStatic;
    private MockedStatic<RegionExplorer> regionExplorerStatic;
    private MockedStatic<Coastal> coastalStatic;
    private MockedStatic<Fluvial> fluvialStatic;

    @BeforeEach
    void setUp() {
        Store.clear();
        CommonConfig.set(new CommonConfig(8, 2, 1, -0.17, 10, 1, 3, 288, false));

        cellCacheStatic = mockStatic(CellCache.class);
        regionCacheStatic = mockStatic(RegionCache.class);
        regionExplorerStatic = mockStatic(RegionExplorer.class);
        coastalStatic = mockStatic(Coastal.class);
        fluvialStatic = mockStatic(Fluvial.class);

        cellCacheStatic.when(CellCache::get).thenReturn(cellCache);
        regionCacheStatic.when(RegionCache::get).thenReturn(regionCache);

        Cell mockCell = mock(Cell.class);
        when(mockCell.averageEstimatedTerrainHeight()).thenReturn(50);
        when(mockCell.averageTemperature()).thenReturn(0.5);
        when(mockCell.averageDepth()).thenReturn(0.1);
        when(mockCell.averageRidges()).thenReturn(0.8);
        when(mockCell.averageVegetation()).thenReturn(0.1);
        when(mockCell.averageErosion()).thenReturn(0.5);
        when(mockCell.hasInflowingNeighbor()).thenReturn(true);
        when(mockCell.pos()).thenReturn(new CellPos(0, 0));
        when(mockCell.feature()).thenReturn(Feature.NONE);
        when(mockCell.withFeature(any())).thenReturn(mockCell);
        when(cellCache.getOrComputeWithFlow(any(CellPos.class))).thenReturn(mockCell);
    }

    @AfterEach
    void tearDown() {
        cellCacheStatic.close();
        regionCacheStatic.close();
        regionExplorerStatic.close();
        coastalStatic.close();
        fluvialStatic.close();
    }

    @Test
    void emptyWorkingSetSkipsBoundaryDetection() {
        ChunkAccess chunk = mockChunk(0, 0);
        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of());

        RiverShaping.shape(chunk, 63, randomState);

        coastalStatic.verify(() -> Coastal.boundaries(any(), any()), never());
        fluvialStatic.verify(() -> Fluvial.boundaries(any(), any()), never());
    }

    @Test
    void coastalRegionGetsCoastalBoundaryDetection() {
        RegionPos pos = new RegionPos(0, 0);
        Region coastal = new Region(pos, RegionType.COASTAL);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(coastal));

        List<Boundary> boundaries = List.of(
            new Boundary(new CellPos(0, 0), new CellPos(1, 0), BoundaryType.OCEAN),
            new Boundary(new CellPos(1, 1), new CellPos(2, 1), BoundaryType.BASIN)
        );
        coastalStatic.when(() -> Coastal.boundaries(pos, cellCache)).thenReturn(boundaries);

        RiverShaping.shape(chunk, 63, randomState);

        coastalStatic.verify(() -> Coastal.boundaries(pos, cellCache));
        fluvialStatic.verify(() -> Fluvial.boundaries(any(), any()), never());
    }

    @Test
    void fluvialRegionGetsFluvialBoundaryDetection() {
        RegionPos pos = new RegionPos(0, 0);
        Region fluvial = new Region(pos, RegionType.FLUVIAL);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(fluvial));

        List<Boundary> boundaries = List.of(
            new Boundary(new CellPos(1, 1), new CellPos(2, 1), BoundaryType.BASIN)
        );
        fluvialStatic.when(() -> Fluvial.boundaries(pos, cellCache)).thenReturn(boundaries);

        RiverShaping.shape(chunk, 63, randomState);

        coastalStatic.verify(() -> Coastal.boundaries(any(), any()), never());
        fluvialStatic.verify(() -> Fluvial.boundaries(pos, cellCache));
    }

    @Test
    void oceanicRegionGetsNoBoundaryDetection() {
        RegionPos pos = new RegionPos(0, 0);
        Region oceanic = new Region(pos, RegionType.OCEANIC);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(oceanic));

        RiverShaping.shape(chunk, 63, randomState);

        coastalStatic.verify(() -> Coastal.boundaries(any(), any()), never());
        fluvialStatic.verify(() -> Fluvial.boundaries(any(), any()), never());
    }

    @Test
    void inlandRegionGetsNoBoundaryDetection() {
        RegionPos pos = new RegionPos(0, 0);
        Region inland = new Region(pos, RegionType.INLAND);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(inland));

        RiverShaping.shape(chunk, 63, randomState);

        coastalStatic.verify(() -> Coastal.boundaries(any(), any()), never());
        fluvialStatic.verify(() -> Fluvial.boundaries(any(), any()), never());
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

        List<Boundary> coastalBoundaries = List.of(
            new Boundary(new CellPos(0, 0), new CellPos(1, 0), BoundaryType.OCEAN)
        );
        coastalStatic.when(() -> Coastal.boundaries(coastalPos, cellCache)).thenReturn(coastalBoundaries);
        fluvialStatic.when(() -> Fluvial.boundaries(any(), any())).thenReturn(List.of());

        RiverShaping.shape(chunk, 63, randomState);

        coastalStatic.verify(() -> Coastal.boundaries(coastalPos, cellCache));
        coastalStatic.verify(() -> Coastal.boundaries(fluvialPos, cellCache), never());
        fluvialStatic.verify(() -> Fluvial.boundaries(coastalPos, cellCache), never());
        fluvialStatic.verify(() -> Fluvial.boundaries(fluvialPos, cellCache));
    }

    @Test
    void enrichedRegionsStoredInCache() {
        RegionPos pos = new RegionPos(0, 0);
        Region coastal = new Region(pos, RegionType.COASTAL);
        ChunkAccess chunk = mockChunk(0, 0);

        regionExplorerStatic.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenReturn(Set.of(coastal));

        List<Boundary> boundaries = List.of(
            new Boundary(new CellPos(0, 0), new CellPos(1, 0), BoundaryType.OCEAN)
        );
        coastalStatic.when(() -> Coastal.boundaries(pos, cellCache)).thenReturn(boundaries);

        RiverShaping.shape(chunk, 63, randomState);

        verify(regionCache).put(any(Region.class));
    }

    private ChunkAccess mockChunk(int chunkX, int chunkZ) {
        ChunkAccess chunk = mock(ChunkAccess.class);
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
        when(chunk.getPos()).thenReturn(chunkPos);
        return chunk;
    }
}
