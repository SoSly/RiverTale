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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FluvialTest {

    @Mock
    CellCache cellCache;

    @BeforeEach
    void setUp() {
        Store.clear();
        CommonConfig.set(new CommonConfig(32, 2, 1, -0.17, 10, 1, 3, 288, false));
    }

    @Test
    void emptyWhenNoBasins() {
        RegionPos region = new RegionPos(0, 0);
        mockCellCache(pos -> createLandCell(pos));

        List<Boundary> boundaries = Fluvial.boundaries(region, cellCache);

        assertTrue(boundaries.isEmpty());
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

        List<Boundary> boundaries = Fluvial.boundaries(region, cellCache);

        assertFalse(boundaries.isEmpty());
        assertTrue(boundaries.stream().allMatch(b -> b.type() == BoundaryType.BASIN));
    }

    @Test
    void noOceanBoundaries() {
        int boundaryCellX = 16;
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

        List<Boundary> boundaries = Fluvial.boundaries(region, cellCache);

        boolean hasOcean = boundaries.stream().anyMatch(b -> b.type() == BoundaryType.OCEAN);
        assertFalse(hasOcean);
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
