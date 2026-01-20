package org.sosly.rivertale.terrain;

import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BasinsTest {

    @Mock
    CellCache cellCache;

    @BeforeEach
    void setUp() {
        Store.clear();
        CommonConfig.set(new CommonConfig(32, 2, 1, -0.17, 10, 1, 3, 288, false));
    }

    @Test
    void boundariesReturnsNullWhenAllCellsAreAboveSeaLevel() {
        RegionPos region = new RegionPos(0, 0);
        mockAllCellsAboveSeaLevel();

        Boundary boundary = Basins.boundaries(region, cellCache);

        assertNull(boundary);
    }

    @Test
    void boundariesReturnsNullWhenAllCellsAreOcean() {
        RegionPos region = new RegionPos(0, 0);
        mockAllCellsOcean();

        Boundary boundary = Basins.boundaries(region, cellCache);

        assertNull(boundary);
    }

    @Test
    void boundariesFindsBasinWhenCellIsBelowSeaLevel() {
        RegionPos region = new RegionPos(0, 0);
        CellPos basinPos = new CellPos(region.getMinCell().x() + 5, region.getMinCell().z() + 5);
        mockWithSingleBasin(basinPos);

        Boundary boundary = Basins.boundaries(region, cellCache);

        assertNotNull(boundary);
        assertEquals(BoundaryType.BASIN, boundary.type());
        assertTrue(boundary.cells().contains(basinPos));
    }

    @Test
    void boundariesExcludesCellWithPartialSamplesAboveSeaLevel() {
        RegionPos region = new RegionPos(0, 0);
        CellPos mixedPos = new CellPos(region.getMinCell().x() + 5, region.getMinCell().z() + 5);
        mockWithMixedCell(mixedPos);

        Boundary boundary = Basins.boundaries(region, cellCache);

        assertNull(boundary);
    }

    private void mockAllCellsAboveSeaLevel() {
        when(cellCache.getOrCompute(any(CellPos.class))).thenAnswer(invocation -> {
            CellPos pos = invocation.getArgument(0);
            double continents = 0.5;
            double depth = 0.0;
            ChunkPos chunk = new ChunkPos(pos.x(), pos.z());
            Sample sample = new Sample(chunk, continents, depth);
            return new Cell(pos, Map.of(chunk, sample));
        });
    }

    private void mockAllCellsOcean() {
        when(cellCache.getOrCompute(any(CellPos.class))).thenAnswer(invocation -> {
            CellPos pos = invocation.getArgument(0);
            double continents = -0.5;
            double depth = -0.1;
            ChunkPos chunk = new ChunkPos(pos.x(), pos.z());
            Sample sample = new Sample(chunk, continents, depth);
            return new Cell(pos, Map.of(chunk, sample));
        });
    }

    private void mockWithSingleBasin(CellPos basinPos) {
        when(cellCache.getOrCompute(any(CellPos.class))).thenAnswer(invocation -> {
            CellPos pos = invocation.getArgument(0);
            ChunkPos chunk = new ChunkPos(pos.x(), pos.z());

            if (pos.equals(basinPos)) {
                double continents = 0.5;
                double depth = -0.1;
                Sample sample = new Sample(chunk, continents, depth);
                return new Cell(pos, Map.of(chunk, sample));
            }

            double continents = 0.5;
            double depth = 0.0;
            Sample sample = new Sample(chunk, continents, depth);
            return new Cell(pos, Map.of(chunk, sample));
        });
    }

    private void mockWithMixedCell(CellPos mixedPos) {
        when(cellCache.getOrCompute(any(CellPos.class))).thenAnswer(invocation -> {
            CellPos pos = invocation.getArgument(0);

            if (pos.equals(mixedPos)) {
                ChunkPos chunk1 = new ChunkPos(pos.x(), pos.z());
                ChunkPos chunk2 = new ChunkPos(pos.x() + 1, pos.z());
                Sample belowSea = new Sample(chunk1, 0.5, -0.1);
                Sample aboveSea = new Sample(chunk2, 0.5, 0.0);
                return new Cell(pos, Map.of(chunk1, belowSea, chunk2, aboveSea));
            }

            ChunkPos chunk = new ChunkPos(pos.x(), pos.z());
            Sample sample = new Sample(chunk, 0.5, 0.0);
            return new Cell(pos, Map.of(chunk, sample));
        });
    }
}
