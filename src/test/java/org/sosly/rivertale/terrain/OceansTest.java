package org.sosly.rivertale.terrain;

import java.util.Map;
import java.util.function.Function;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.metric.Store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OceansTest {

    @Mock
    CellCache cellCache;

    @BeforeEach
    void setUp() {
        Store.clear();
        CommonConfig.set(new CommonConfig(32, 2, 1, -0.17, 10, 1, 3, 288, false));
    }

    @Test
    void boundariesReturnsNullWhenAllCellsAreOcean() {
        RegionPos region = new RegionPos(0, 0);
        mockCellCache(pos -> true);

        Boundary boundary = Oceans.boundaries(region, cellCache);

        assertNull(boundary);
    }

    @Test
    void boundariesReturnsNullWhenAllCellsAreLandWithNoOceanNeighbors() {
        RegionPos region = new RegionPos(0, 0);
        mockCellCache(pos -> false);

        Boundary boundary = Oceans.boundaries(region, cellCache);

        assertNull(boundary);
    }

    @Test
    void boundariesFindsBoundaryAtOceanLandTransition() {
        int boundaryCellX = 16;
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        mockCellCache(pos -> pos.x() >= minCell.x() + boundaryCellX);

        Boundary boundary = Oceans.boundaries(region, cellCache);

        assertNotNull(boundary);
        assertEquals(BoundaryType.OCEAN, boundary.type());
        assertFalse(boundary.cells().isEmpty());
    }

    @Test
    void boundaryCellsAreLandCellsNotOceanCells() {
        int boundaryCellX = 16;
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        mockCellCache(pos -> pos.x() >= minCell.x() + boundaryCellX);

        Boundary boundary = Oceans.boundaries(region, cellCache);

        assertNotNull(boundary);
        for (CellPos cellPos : boundary.cells()) {
            Cell cell = cellCache.getOrCompute(cellPos);
            assertFalse(cell.isOcean(), "Boundary cell should be land, not ocean: " + cellPos);
        }
    }

    @Test
    void boundaryCellsHaveOceanNeighbors() {
        int boundaryCellX = 16;
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        mockCellCache(pos -> pos.x() >= minCell.x() + boundaryCellX);

        Boundary boundary = Oceans.boundaries(region, cellCache);

        assertNotNull(boundary);
        for (CellPos cellPos : boundary.cells()) {
            boolean hasOceanNeighbor = false;
            for (FlowDirection dir : FlowDirection.D4) {
                CellPos neighborPos = cellPos.relative(dir);
                Cell neighbor = cellCache.getOrCompute(neighborPos);
                if (neighbor.isOcean()) {
                    hasOceanNeighbor = true;
                    break;
                }
            }
            assertTrue(hasOceanNeighbor, "Boundary cell should have ocean neighbor: " + cellPos);
        }
    }

    @Test
    void boundaryContainsEachLandCellOnlyOnce() {
        int boundaryCellX = 16;
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        mockCellCache(pos -> pos.x() >= minCell.x() + boundaryCellX);

        Boundary boundary = Oceans.boundaries(region, cellCache);

        assertNotNull(boundary);
        long uniqueCount = boundary.cells().stream().distinct().count();
        assertEquals(boundary.cells().size(), uniqueCount, "Boundary should not contain duplicate cells");
    }

    private void mockCellCache(Function<CellPos, Boolean> isOcean) {
        when(cellCache.getOrCompute(any(CellPos.class))).thenAnswer(invocation -> {
            CellPos pos = invocation.getArgument(0);
            double continents = isOcean.apply(pos) ? -0.5 : 0.5;
            ChunkPos chunk = new ChunkPos(pos.x(), pos.z());
            Sample sample = new Sample(chunk, continents, 0.5);
            return new Cell(pos, Map.of(chunk, sample));
        });
    }
}
