package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.Coastal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShoreRoutingTest {
    private final CellPos source = new CellPos(3, 3);
    private final CellPos water = new CellPos(4, 4);
    private final CellPos shore = new CellPos(3, 4);
    private final Map<CellPos, Cell> cells = new HashMap<>();
    private CommonConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, 0, 1, 3, 288, false, true, 12,
            5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT, 5000, false));
    }

    @AfterEach
    void tearDown() {
        CommonConfig.set(originalConfig);
    }

    @Test
    void diagonalWaterEntryCannotReturnToLandUsingFallbackForOcean() {
        diagonalWaterEntryCannotReturnToLandUsingFallback(BoundaryType.OCEAN);
    }

    @Test
    void diagonalWaterEntryCannotReturnToLandUsingFallbackForBasin() {
        diagonalWaterEntryCannotReturnToLandUsingFallback(BoundaryType.BASIN);
    }

    private void diagonalWaterEntryCannotReturnToLandUsingFallback(BoundaryType type) {
        FlowlineTracer tracer = tracer(type, 5);

        Flowline path = tracer.trace(source);

        assertTrue(path.isValid());
        assertEquals(List.of(source, shore), path.cells());
        assertEquals(shore, path.terminus());
        assertFalse(WatershedBuilder.build(List.of(path)).get(0).contains(water));
    }

    @Test
    void waterIsExcludedEvenWhenNoLandRouteSucceedsForOcean() {
        waterIsExcludedEvenWhenNoLandRouteSucceeds(BoundaryType.OCEAN);
    }

    @Test
    void waterIsExcludedEvenWhenNoLandRouteSucceedsForBasin() {
        waterIsExcludedEvenWhenNoLandRouteSucceeds(BoundaryType.BASIN);
    }

    private void waterIsExcludedEvenWhenNoLandRouteSucceeds(BoundaryType type) {
        CommonConfig.set(new CommonConfig(8, 1, 1, 0, 1, 3, 288, false, false, 12,
            5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT, 5000, false));
        FlowlineTracer tracer = tracer(type, 0);

        Flowline path = tracer.trace(source);

        assertFalse(path.isValid());
        assertEquals(List.of(source), path.cells());
    }

    @Test
    void reverseDiscoveryCannotTreatWaterAsAnUpstreamRiverCellForOcean() {
        reverseDiscoveryCannotTreatWaterAsAnUpstreamRiverCell(BoundaryType.OCEAN);
    }

    @Test
    void reverseDiscoveryCannotTreatWaterAsAnUpstreamRiverCellForBasin() {
        reverseDiscoveryCannotTreatWaterAsAnUpstreamRiverCell(BoundaryType.BASIN);
    }

    private void reverseDiscoveryCannotTreatWaterAsAnUpstreamRiverCell(BoundaryType type) {
        FlowlineTracer tracer = tracer(type, 5);

        assertTrue(tracer.predecessors(water).isEmpty());
        assertFalse(tracer.predecessors(shore).contains(water));
        assertTrue(tracer.predecessors(shore).contains(source));
    }

    @Test
    void aWaterCellCannotStartARouteBackOntoLandForOcean() {
        aWaterCellCannotStartARouteBackOntoLand(BoundaryType.OCEAN);
    }

    @Test
    void aWaterCellCannotStartARouteBackOntoLandForBasin() {
        aWaterCellCannotStartARouteBackOntoLand(BoundaryType.BASIN);
    }

    private void aWaterCellCannotStartARouteBackOntoLand(BoundaryType type) {
        assertFalse(tracer(type, 5).trace(water).isValid());
    }

    private FlowlineTracer tracer(BoundaryType type, int fallback) {
        CellCache cache = mock(CellCache.class);
        when(cache.getOrCompute(any(CellPos.class))).thenAnswer(invocation -> cell(invocation.getArgument(0), type));
        RegionPos pos = new RegionPos(0, 0);
        Region region = new Region(pos, RegionType.COASTAL, Coastal.boundaries(pos, cache));
        assertEquals(4, region.boundaries().size());
        assertTrue(region.boundaries().stream().noneMatch(boundary -> boundary.land().equals(source)));
        return new FlowlineTracer(List.of(region), key -> cell(key, type), fallback, 10);
    }

    private Cell cell(CellPos pos, BoundaryType type) {
        return cells.computeIfAbsent(pos, key -> {
            Cell cell = mock(Cell.class);
            when(cell.pos()).thenReturn(key);
            when(cell.feature()).thenReturn(Feature.NONE);
            when(cell.isOcean()).thenReturn(key.equals(water) && type == BoundaryType.OCEAN);
            when(cell.isBasin()).thenReturn(key.equals(water) && type == BoundaryType.BASIN);
            when(cell.averageEstimatedTerrainHeight()).thenReturn(key.equals(water) ? 55 : 80);
            when(cell.flowDirections()).thenReturn(key.equals(source) ? List.of(FlowDirection.SOUTHEAST) : List.of());
            return cell;
        });
    }
}
