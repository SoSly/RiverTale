package org.sosly.rivertale.terrain;

import java.util.List;
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
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.metric.Store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoastalTest {

    @Mock
    CellCache cellCache;

    @BeforeEach
    void setUp() {
        Store.clear();
        CommonConfig.set(new CommonConfig(32, 2, 1, -0.17, 10, 1, 3, 288, false));
    }

    @Test
    void emptyWhenAllOcean() {
        RegionPos region = new RegionPos(0, 0);
        mockCellCache(pos -> createOceanCell(pos));

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        assertTrue(boundaries.isEmpty());
    }

    @Test
    void emptyWhenAllLandWithNoWaterNeighbors() {
        RegionPos region = new RegionPos(0, 0);
        mockCellCache(pos -> createLandCell(pos));

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        assertTrue(boundaries.isEmpty());
    }

    @Test
    void findsOceanBoundary() {
        int boundaryCellX = 16;
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        mockCellCache(pos -> {
            if (pos.x() >= minCell.x() + boundaryCellX) {
                return createOceanCell(pos);
            }
            return createLandCell(pos);
        });

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        assertTrue(boundaries.stream().anyMatch(b -> b.type() == BoundaryType.OCEAN));
    }

    @Test
    void findsBasinBoundary() {
        RegionPos region = new RegionPos(0, 0);
        CellPos basinPos = new CellPos(region.getMinCell().x() + 10, region.getMinCell().z() + 10);
        mockCellCache(pos -> {
            if (pos.equals(basinPos)) {
                return createBasinCell(pos);
            }
            return createLandCell(pos);
        });

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        assertTrue(boundaries.stream().anyMatch(b -> b.type() == BoundaryType.BASIN));
    }

    @Test
    void findsBothBoundaryTypes() {
        int boundaryCellX = 20;
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        CellPos basinPos = new CellPos(minCell.x() + 10, minCell.z() + 10);
        mockCellCache(pos -> {
            if (pos.x() >= minCell.x() + boundaryCellX) {
                return createOceanCell(pos);
            }
            if (pos.equals(basinPos)) {
                return createBasinCell(pos);
            }
            return createLandCell(pos);
        });

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        boolean hasOcean = boundaries.stream().anyMatch(b -> b.type() == BoundaryType.OCEAN);
        boolean hasBasin = boundaries.stream().anyMatch(b -> b.type() == BoundaryType.BASIN);
        assertTrue(hasOcean);
        assertTrue(hasBasin);
    }

    @Test
    void multipleFacesPerCell() {
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        CellPos peninsulaCell = new CellPos(minCell.x() + 10, minCell.z() + 10);
        mockCellCache(pos -> {
            if (pos.equals(peninsulaCell)) {
                return createLandCell(pos);
            }
            return createOceanCell(pos);
        });

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        long boundariesForPeninsula = boundaries.stream()
            .filter(b -> b.land().equals(peninsulaCell))
            .count();
        assertEquals(4, boundariesForPeninsula);
    }

    private void mockCellCache(Function<CellPos, Cell> cellFactory) {
        when(cellCache.getOrCompute(any(CellPos.class))).thenAnswer(invocation -> {
            CellPos pos = invocation.getArgument(0);
            return cellFactory.apply(pos);
        });
    }

    private Cell createOceanCell(CellPos pos) {
        ChunkPos chunk = new ChunkPos(pos.x(), pos.z());
        Sample sample = new Sample(chunk, -0.5, 0.5);
        return new Cell(pos, Map.of(chunk, sample));
    }

    private Cell createLandCell(CellPos pos) {
        ChunkPos chunk = new ChunkPos(pos.x(), pos.z());
        Sample sample = new Sample(chunk, 0.5, 0.5);
        return new Cell(pos, Map.of(chunk, sample));
    }

    private Cell createBasinCell(CellPos pos) {
        ChunkPos chunk = new ChunkPos(pos.x(), pos.z());
        Sample sample = new Sample(chunk, 0.5, -0.1);
        return new Cell(pos, Map.of(chunk, sample));
    }
}
