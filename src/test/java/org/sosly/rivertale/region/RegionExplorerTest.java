package org.sosly.rivertale.region;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.river.Flowline;
import org.sosly.rivertale.river.FlowlineTracer;
import org.sosly.rivertale.river.RiverPreparation;

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
    private MockedStatic<RegionTypeCache> typeCacheStatic;
    private RegionTypeCache typeCache;

    @BeforeEach
    void setUp() {
        Store.clear();
        CommonConfig.set(new CommonConfig(8, 2, 1, 10, 1, 3, 288, false, false, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT, 5000, false));

        cellCacheStatic = mockStatic(CellCache.class);
        regionCacheStatic = mockStatic(RegionCache.class);
        sampleCacheStatic = mockStatic(SampleCache.class);

        cellCacheStatic.when(CellCache::get).thenReturn(cellCache);
        regionCacheStatic.when(RegionCache::get).thenReturn(regionCache);
        sampleCacheStatic.when(SampleCache::get).thenReturn(sampleCache);
        typeCache = mock(RegionTypeCache.class);
        when(typeCache.getOrCompute(any(RegionPos.class), any(SampleCache.class)))
            .thenAnswer(call -> regionCache.getOrCompute((RegionPos) call.getArgument(0)).type());
        typeCacheStatic = mockStatic(RegionTypeCache.class);
        typeCacheStatic.when(RegionTypeCache::get).thenReturn(typeCache);
    }

    @AfterEach
    void tearDown() {
        cellCacheStatic.close();
        regionCacheStatic.close();
        sampleCacheStatic.close();
        typeCacheStatic.close();
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
    void inlandCenterRemainsAvailableForDiagnostics() {
        RegionPos centerPos = new RegionPos(0, 0);
        Region centerRegion = new Region(centerPos, RegionType.INLAND);
        when(regionCache.getOrCompute(any(RegionPos.class))).thenReturn(centerRegion);

        BlockPos blockPos = centerPos.getWorldPosition(0);
        Set<Region> result = RegionExplorer.discover(blockPos);

        assertEquals(Set.of(centerRegion), result);
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

    @Test
    void inlandWithoutFluvialStaysWithinOneEdgeNeighbor() {
        Map<RegionPos, Region> regions = inlandMap();
        RegionPos center = new RegionPos(0, 0);
        regions.put(new RegionPos(1, 1), new Region(new RegionPos(1, 1), RegionType.FLUVIAL));

        Set<Region> result = RegionExplorer.discover(center.getWorldPosition(0));

        assertEquals(Set.of(center, new RegionPos(0, -1), new RegionPos(1, 0),
            new RegionPos(0, 1), new RegionPos(-1, 0)),
            result.stream().map(Region::pos).collect(Collectors.toSet()));
    }

    @Test
    void inlandAdmitsFluvialAndItsCoastWithoutRequiringBorderFlow() {
        Map<RegionPos, Region> regions = inlandMap();
        RegionPos center = new RegionPos(0, 0);
        Region fluvial = new Region(new RegionPos(1, 0), RegionType.FLUVIAL);
        Region coastal = new Region(new RegionPos(2, 0), RegionType.COASTAL);
        regions.put(fluvial.pos(), fluvial);
        regions.put(coastal.pos(), coastal);
        regions.put(new RegionPos(3, 0), new Region(new RegionPos(3, 0), RegionType.COASTAL));

        Set<Region> result = RegionExplorer.discover(center.getWorldPosition(0));

        assertEquals(Set.of(regions.get(center), fluvial, coastal), result);
    }

    @Test
    void inlandPathCanCrossFluvialToReachTheSea() {
        Map<RegionPos, Region> regions = inlandMap();
        CellPos terminus = new CellPos(18, 2);
        regions.put(new RegionPos(1, 0), new Region(new RegionPos(1, 0), RegionType.FLUVIAL));
        regions.put(terminus.getRegion(), new Region(terminus.getRegion(), RegionType.COASTAL,
            List.of(new Boundary(terminus, new CellPos(19, 2), BoundaryType.OCEAN))));
        when(cellCache.getOrComputeWithFlow(any(CellPos.class))).thenAnswer(invocation ->
            new Cell(invocation.getArgument(0), Set.of()).withFlowDirections(List.of(FlowDirection.EAST)));

        Flowline path = new FlowlineTracer(RiverPreparation.forCells(List.of(new CellPos(1, 2))))
            .trace(new CellPos(1, 2));

        assertTrue(path.isValid());
        assertEquals(terminus, path.terminus());
        assertTrue(path.cells().stream().anyMatch(pos -> pos.getRegion().equals(new RegionPos(1, 0))));
    }

    @Test
    void inlandPathCanEndAtANearerBasinInItsFluvialCorridor() {
        Map<RegionPos, Region> regions = inlandMap();
        CellPos basin = new CellPos(10, 2);
        CellPos ocean = new CellPos(18, 2);
        regions.put(basin.getRegion(), new Region(basin.getRegion(), RegionType.FLUVIAL,
            List.of(new Boundary(basin, new CellPos(11, 2), BoundaryType.BASIN))));
        regions.put(ocean.getRegion(), new Region(ocean.getRegion(), RegionType.COASTAL,
            List.of(new Boundary(ocean, new CellPos(19, 2), BoundaryType.OCEAN))));
        when(cellCache.getOrComputeWithFlow(any(CellPos.class))).thenAnswer(invocation ->
            new Cell(invocation.getArgument(0), Set.of()).withFlowDirections(List.of(FlowDirection.EAST)));

        Flowline path = new FlowlineTracer(RiverPreparation.forCells(List.of(new CellPos(1, 2))))
            .trace(new CellPos(1, 2));

        assertTrue(path.isValid());
        assertEquals(basin, path.terminus());
    }

    @Test
    void isolatedInlandCannotReachNeighborOrDistantBasins() {
        Map<RegionPos, Region> regions = inlandMap();
        CellPos basin = new CellPos(10, 2);
        Region neighbor = new Region(basin.getRegion(), RegionType.INLAND,
            List.of(new Boundary(basin, new CellPos(11, 2), BoundaryType.BASIN)));
        regions.put(neighbor.pos(), neighbor);
        when(cellCache.getOrComputeWithFlow(any(CellPos.class))).thenAnswer(invocation ->
            new Cell(invocation.getArgument(0), Set.of()).withFlowDirections(List.of(FlowDirection.EAST)));

        Flowline path = new FlowlineTracer(RiverPreparation.forCells(List.of(new CellPos(1, 2))))
            .trace(new CellPos(1, 2));
        assertFalse(path.isValid());
        assertEquals(List.of(new CellPos(1, 2)), path.cells());

        regions.put(neighbor.pos(), new Region(neighbor.pos(), RegionType.INLAND));
        CellPos distant = new CellPos(18, 2);
        regions.put(distant.getRegion(), new Region(distant.getRegion(), RegionType.INLAND,
            List.of(new Boundary(distant, new CellPos(19, 2), BoundaryType.BASIN))));
        Flowline rejected = new FlowlineTracer(RiverPreparation.forCells(List.of(new CellPos(1, 2))))
            .trace(new CellPos(1, 2));
        assertFalse(rejected.isValid());
        assertEquals(List.of(new CellPos(1, 2)), rejected.cells());
    }

    @Test
    void fluvialDiscoveryDoesNotPrepareNonCoastalNeighbors() {
        RegionPos center = new RegionPos(0, 0);
        RegionPos coast = new RegionPos(1, 0);
        Region origin = new Region(center, RegionType.FLUVIAL);
        Region outlet = new Region(coast, RegionType.COASTAL);
        when(typeCache.getOrCompute(any(RegionPos.class), any(SampleCache.class))).thenAnswer(call ->
            coast.equals(call.getArgument(0)) ? RegionType.COASTAL : RegionType.INLAND);
        when(regionCache.getOrCompute(any(RegionPos.class))).thenAnswer(call -> {
            RegionPos pos = call.getArgument(0);
            if (pos.equals(center)) {
                return origin;
            }
            if (pos.equals(coast)) {
                return outlet;
            }
            throw new AssertionError("Non-coastal region was fully prepared: " + pos);
        });
        when(cellCache.getOrComputeWithFlow(any(CellPos.class))).thenAnswer(call ->
            new Cell(call.getArgument(0), Set.of()).withFlowDirections(List.of(FlowDirection.EAST)));

        assertEquals(Set.of(origin, outlet), RegionExplorer.discover(center.getWorldPosition(0)));
    }

    private Map<RegionPos, Region> inlandMap() {
        Map<RegionPos, Region> regions = new HashMap<>();
        when(regionCache.getOrCompute(any(RegionPos.class))).thenAnswer(invocation -> {
            RegionPos pos = invocation.getArgument(0);
            return regions.computeIfAbsent(pos, key -> new Region(key, RegionType.INLAND));
        });
        return regions;
    }
    private Cell mockCellWithFlow(CellPos pos, List<FlowDirection> flowDirections) {
        Cell cell = mock(Cell.class);
        when(cell.pos()).thenReturn(pos);
        when(cell.flowDirections()).thenReturn(flowDirections);
        return cell;
    }
}
