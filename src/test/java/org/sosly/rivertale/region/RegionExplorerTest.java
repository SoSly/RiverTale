package org.sosly.rivertale.region;

import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
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
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RegionExplorerTest {

    @Mock
    CellCache cellCache;

    @Mock
    RegionCache regionCache;

    @Mock
    SampleCache sampleCache;

    private MockedStatic<CellCache> cellCacheStatic;
    private MockedStatic<RegionCache> regionCacheStatic;
    private MockedStatic<SampleCache> sampleCacheStatic;

    @BeforeEach
    void setUp() {
        Store.clear();
        CommonConfig.set(new CommonConfig(8, 2, 1, -0.17, 10, 1, 3, 288, false));

        cellCacheStatic = mockStatic(CellCache.class);
        regionCacheStatic = mockStatic(RegionCache.class);
        sampleCacheStatic = mockStatic(SampleCache.class);

        cellCacheStatic.when(CellCache::get).thenReturn(cellCache);
        regionCacheStatic.when(RegionCache::get).thenReturn(regionCache);
        sampleCacheStatic.when(SampleCache::get).thenReturn(sampleCache);
    }

    @AfterEach
    void tearDown() {
        cellCacheStatic.close();
        regionCacheStatic.close();
        sampleCacheStatic.close();
    }

    @Test
    void oceanicCenterReturnsEmptySet() {
        RegionPos centerPos = new RegionPos(0, 0);
        Region centerRegion = new Region(centerPos, RegionType.OCEANIC);
        when(regionCache.getOrCompute(any(RegionPos.class))).thenReturn(centerRegion);

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertTrue(result.isEmpty());
    }

    @Test
    void inlandCenterReturnsEmptySet() {
        RegionPos centerPos = new RegionPos(0, 0);
        Region centerRegion = new Region(centerPos, RegionType.INLAND);
        when(regionCache.getOrCompute(any(RegionPos.class))).thenReturn(centerRegion);

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertTrue(result.isEmpty());
    }

    @Test
    void coastalWithNoFluvialNeighborsReturnsCenterOnly() {
        RegionPos centerPos = new RegionPos(0, 0);
        Region centerRegion = new Region(centerPos, RegionType.COASTAL);

        when(regionCache.getOrCompute(any(RegionPos.class))).thenAnswer(invocation -> {
            RegionPos pos = invocation.getArgument(0);
            if (pos.equals(centerPos)) {
                return centerRegion;
            }
            return new Region(pos, RegionType.OCEANIC);
        });

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertEquals(1, result.size());
        assertTrue(result.contains(centerRegion));
    }

    @Test
    void coastalWithFluvialFlowingInReturnsSetOfTwo() {
        RegionPos centerPos = new RegionPos(0, 0);
        RegionPos northPos = centerPos.relative(FlowDirection.NORTH);
        Region centerRegion = new Region(centerPos, RegionType.COASTAL);
        Region northRegion = new Region(northPos, RegionType.FLUVIAL);

        when(regionCache.getOrCompute(any(RegionPos.class))).thenAnswer(invocation -> {
            RegionPos pos = invocation.getArgument(0);
            if (pos.equals(centerPos)) {
                return centerRegion;
            }
            if (pos.equals(northPos)) {
                return northRegion;
            }
            return new Region(pos, RegionType.OCEANIC);
        });

        List<CellPos> southBorderCells = northPos.getBorderCells(FlowDirection.SOUTH);
        for (CellPos cellPos : southBorderCells) {
            Cell cell = mockCellWithFlow(cellPos, List.of(FlowDirection.SOUTH));
            when(cellCache.getOrComputeWithFlow(cellPos)).thenReturn(cell);
        }

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertEquals(2, result.size());
        assertTrue(result.contains(centerRegion));
        assertTrue(result.contains(northRegion));
    }

    @Test
    void coastalWithFluvialFlowingAwayReturnsCenterOnly() {
        RegionPos centerPos = new RegionPos(0, 0);
        RegionPos northPos = centerPos.relative(FlowDirection.NORTH);
        Region centerRegion = new Region(centerPos, RegionType.COASTAL);
        Region northRegion = new Region(northPos, RegionType.FLUVIAL);

        when(regionCache.getOrCompute(any(RegionPos.class))).thenAnswer(invocation -> {
            RegionPos pos = invocation.getArgument(0);
            if (pos.equals(centerPos)) {
                return centerRegion;
            }
            if (pos.equals(northPos)) {
                return northRegion;
            }
            return new Region(pos, RegionType.OCEANIC);
        });

        List<CellPos> southBorderCells = northPos.getBorderCells(FlowDirection.SOUTH);
        for (CellPos cellPos : southBorderCells) {
            Cell cell = mockCellWithFlow(cellPos, List.of(FlowDirection.NORTH));
            when(cellCache.getOrComputeWithFlow(cellPos)).thenReturn(cell);
        }

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertEquals(1, result.size());
        assertTrue(result.contains(centerRegion));
        assertFalse(result.contains(northRegion));
    }

    @Test
    void fluvialDrainsToCoastalReturnsSetOfTwo() {
        RegionPos centerPos = new RegionPos(0, 0);
        RegionPos southPos = centerPos.relative(FlowDirection.SOUTH);
        Region centerRegion = new Region(centerPos, RegionType.FLUVIAL);
        Region southRegion = new Region(southPos, RegionType.COASTAL);

        when(regionCache.getOrCompute(any(RegionPos.class))).thenAnswer(invocation -> {
            RegionPos pos = invocation.getArgument(0);
            if (pos.equals(centerPos)) {
                return centerRegion;
            }
            if (pos.equals(southPos)) {
                return southRegion;
            }
            return new Region(pos, RegionType.INLAND);
        });

        List<CellPos> southBorderCells = centerPos.getBorderCells(FlowDirection.SOUTH);
        for (CellPos cellPos : southBorderCells) {
            Cell cell = mockCellWithFlow(cellPos, List.of(FlowDirection.SOUTH));
            when(cellCache.getOrComputeWithFlow(cellPos)).thenReturn(cell);
        }

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertEquals(2, result.size());
        assertTrue(result.contains(centerRegion));
        assertTrue(result.contains(southRegion));
    }

    @Test
    void fluvialWithNoDrainageReturnsCenterOnly() {
        RegionPos centerPos = new RegionPos(0, 0);
        RegionPos southPos = centerPos.relative(FlowDirection.SOUTH);
        Region centerRegion = new Region(centerPos, RegionType.FLUVIAL);
        Region southRegion = new Region(southPos, RegionType.COASTAL);

        when(regionCache.getOrCompute(any(RegionPos.class))).thenAnswer(invocation -> {
            RegionPos pos = invocation.getArgument(0);
            if (pos.equals(centerPos)) {
                return centerRegion;
            }
            if (pos.equals(southPos)) {
                return southRegion;
            }
            return new Region(pos, RegionType.INLAND);
        });

        List<CellPos> southBorderCells = centerPos.getBorderCells(FlowDirection.SOUTH);
        for (CellPos cellPos : southBorderCells) {
            Cell cell = mockCellWithFlow(cellPos, List.of(FlowDirection.NORTH));
            when(cellCache.getOrComputeWithFlow(cellPos)).thenReturn(cell);
        }

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertEquals(1, result.size());
        assertTrue(result.contains(centerRegion));
        assertFalse(result.contains(southRegion));
    }

    @Test
    void fluvialWithMultipleDrainageReturnsSetOfThree() {
        RegionPos centerPos = new RegionPos(0, 0);
        RegionPos eastPos = centerPos.relative(FlowDirection.EAST);
        RegionPos westPos = centerPos.relative(FlowDirection.WEST);
        Region centerRegion = new Region(centerPos, RegionType.FLUVIAL);
        Region eastRegion = new Region(eastPos, RegionType.COASTAL);
        Region westRegion = new Region(westPos, RegionType.COASTAL);

        when(regionCache.getOrCompute(any(RegionPos.class))).thenAnswer(invocation -> {
            RegionPos pos = invocation.getArgument(0);
            if (pos.equals(centerPos)) {
                return centerRegion;
            }
            if (pos.equals(eastPos)) {
                return eastRegion;
            }
            if (pos.equals(westPos)) {
                return westRegion;
            }
            return new Region(pos, RegionType.INLAND);
        });

        List<CellPos> eastBorderCells = centerPos.getBorderCells(FlowDirection.EAST);
        for (CellPos cellPos : eastBorderCells) {
            Cell cell = mockCellWithFlow(cellPos, List.of(FlowDirection.EAST));
            when(cellCache.getOrComputeWithFlow(cellPos)).thenReturn(cell);
        }

        List<CellPos> westBorderCells = centerPos.getBorderCells(FlowDirection.WEST);
        for (CellPos cellPos : westBorderCells) {
            Cell cell = mockCellWithFlow(cellPos, List.of(FlowDirection.WEST));
            when(cellCache.getOrComputeWithFlow(cellPos)).thenReturn(cell);
        }

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertEquals(3, result.size());
        assertTrue(result.contains(centerRegion));
        assertTrue(result.contains(eastRegion));
        assertTrue(result.contains(westRegion));
    }

    @Test
    void worldBorderExcludesOutOfBoundsNeighbor() {
        int extremeX = -30_000_000 / CommonConfig.get().regionBlocks();
        RegionPos centerPos = new RegionPos(extremeX, 0);
        RegionPos westPos = centerPos.relative(FlowDirection.WEST);
        RegionPos eastPos = centerPos.relative(FlowDirection.EAST);
        Region centerRegion = new Region(centerPos, RegionType.FLUVIAL);
        Region eastRegion = new Region(eastPos, RegionType.COASTAL);

        assertTrue(westPos.containsOutOfBoundsCells());
        assertFalse(centerPos.containsOutOfBoundsCells());

        when(regionCache.getOrCompute(any(RegionPos.class))).thenAnswer(invocation -> {
            RegionPos pos = invocation.getArgument(0);
            if (pos.equals(centerPos)) {
                return centerRegion;
            }
            if (pos.equals(eastPos)) {
                return eastRegion;
            }
            return new Region(pos, RegionType.INLAND);
        });

        List<CellPos> eastBorderCells = centerPos.getBorderCells(FlowDirection.EAST);
        for (CellPos cellPos : eastBorderCells) {
            Cell cell = mockCellWithFlow(cellPos, List.of(FlowDirection.EAST));
            when(cellCache.getOrComputeWithFlow(cellPos)).thenReturn(cell);
        }

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertEquals(2, result.size());
        assertTrue(result.contains(centerRegion));
        assertTrue(result.contains(eastRegion));
        assertFalse(result.stream().anyMatch(r -> r.pos().equals(westPos)));
    }

    @Test
    void cornerDiagonalFlowDoesNotQualify() {
        RegionPos centerPos = new RegionPos(0, 0);
        RegionPos northPos = centerPos.relative(FlowDirection.NORTH);
        Region centerRegion = new Region(centerPos, RegionType.COASTAL);
        Region northRegion = new Region(northPos, RegionType.FLUVIAL);

        when(regionCache.getOrCompute(any(RegionPos.class))).thenAnswer(invocation -> {
            RegionPos pos = invocation.getArgument(0);
            if (pos.equals(centerPos)) {
                return centerRegion;
            }
            if (pos.equals(northPos)) {
                return northRegion;
            }
            return new Region(pos, RegionType.OCEANIC);
        });

        List<CellPos> southBorderCells = northPos.getBorderCells(FlowDirection.SOUTH);
        for (CellPos cellPos : southBorderCells) {
            Cell cell = mockCellWithFlow(cellPos, List.of(FlowDirection.NORTHEAST));
            when(cellCache.getOrComputeWithFlow(cellPos)).thenReturn(cell);
        }

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertEquals(1, result.size());
        assertTrue(result.contains(centerRegion));
        assertFalse(result.contains(northRegion));
    }

    private Cell mockCellWithFlow(CellPos pos, List<FlowDirection> flowDirections) {
        Cell cell = mock(Cell.class);
        when(cell.pos()).thenReturn(pos);
        when(cell.flowDirections()).thenReturn(flowDirections);
        return cell;
    }
}
