package org.sosly.rivertale.cell;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.density.Sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CellTest {

    @Mock
    CellCache cache;

    @Test
    void flowDirectionReturnsNoneWhenFlat() {
        CellPos pos = new CellPos(0, 0);
        Cell cell = cellAt(pos, 0.5);
        mockNeighbors(pos, 0.5);

        Direction flow = cell.flowDirection(cache);

        assertEquals(Direction.NONE, flow);
    }

    @Test
    void flowDirectionReturnsNorthWhenNorthIsLowest() {
        CellPos pos = new CellPos(0, 0);
        Cell cell = cellAt(pos, 0.5);
        mockNeighborsExcept(pos, 0.5, Direction.NORTH, 0.2);

        Direction flow = cell.flowDirection(cache);

        assertEquals(Direction.NORTH, flow);
    }

    @Test
    void flowDirectionReturnsSouthWhenSouthIsLowest() {
        CellPos pos = new CellPos(0, 0);
        Cell cell = cellAt(pos, 0.5);
        mockNeighborsExcept(pos, 0.5, Direction.SOUTH, 0.2);

        Direction flow = cell.flowDirection(cache);

        assertEquals(Direction.SOUTH, flow);
    }

    @Test
    void flowDirectionReturnsEastWhenEastIsLowest() {
        CellPos pos = new CellPos(0, 0);
        Cell cell = cellAt(pos, 0.5);
        mockNeighborsExcept(pos, 0.5, Direction.EAST, 0.2);

        Direction flow = cell.flowDirection(cache);

        assertEquals(Direction.EAST, flow);
    }

    @Test
    void flowDirectionReturnsWestWhenWestIsLowest() {
        CellPos pos = new CellPos(0, 0);
        Cell cell = cellAt(pos, 0.5);
        mockNeighborsExcept(pos, 0.5, Direction.WEST, 0.2);

        Direction flow = cell.flowDirection(cache);

        assertEquals(Direction.WEST, flow);
    }

    @Test
    void flowDirectionReturnsDiagonalWhenDiagonalIsLowest() {
        CellPos pos = new CellPos(0, 0);
        Cell cell = cellAt(pos, 0.5);
        mockNeighborsExcept(pos, 0.5, Direction.NORTHEAST, 0.0);

        Direction flow = cell.flowDirection(cache);

        assertEquals(Direction.NORTHEAST, flow);
    }

    @Test
    void flowDirectionPrefersCardinalOverDiagonalWhenSlopeEqual() {
        CellPos pos = new CellPos(0, 0);
        Cell cell = cellAt(pos, 1.0);

        for (Direction dir : Direction.D8) {
            CellPos neighborPos = pos.relative(dir);
            double depth = (dir.dx != 0 && dir.dz != 0) ? 1.0 - 0.3 * Math.sqrt(2) : 0.7;
            when(cache.getOrCompute(neighborPos)).thenReturn(cellAt(neighborPos, depth));
        }

        Direction flow = cell.flowDirection(cache);

        assertEquals(Direction.NORTH, flow);
    }

    @Test
    void flowDirectionReturnsNoneWhenUphillEverywhere() {
        CellPos pos = new CellPos(0, 0);
        Cell cell = cellAt(pos, 0.2);
        mockNeighbors(pos, 0.5);

        Direction flow = cell.flowDirection(cache);

        assertEquals(Direction.NONE, flow);
    }

    @Test
    void flowDirectionPicksSteepestWhenMultipleDownhill() {
        CellPos pos = new CellPos(0, 0);
        Cell cell = cellAt(pos, 1.0);

        when(cache.getOrCompute(pos.relative(Direction.NORTH))).thenReturn(cellAt(pos.relative(Direction.NORTH), 0.8));
        when(cache.getOrCompute(pos.relative(Direction.SOUTH))).thenReturn(cellAt(pos.relative(Direction.SOUTH), 0.3));
        when(cache.getOrCompute(pos.relative(Direction.EAST))).thenReturn(cellAt(pos.relative(Direction.EAST), 0.9));
        when(cache.getOrCompute(pos.relative(Direction.WEST))).thenReturn(cellAt(pos.relative(Direction.WEST), 0.9));
        for (Direction dir : new Direction[]{Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.SOUTHWEST}) {
            when(cache.getOrCompute(pos.relative(dir))).thenReturn(cellAt(pos.relative(dir), 0.9));
        }

        Direction flow = cell.flowDirection(cache);

        assertEquals(Direction.SOUTH, flow);
    }

    private Cell cellAt(CellPos pos, double depth) {
        ChunkPos chunk = new ChunkPos(pos.getMiddleBlockX() >> 4, pos.getMiddleBlockZ() >> 4);
        Sample sample = new Sample(chunk, 0.5, depth, 0, 0, 0, 0);
        return new Cell(pos, sample, Feature.DEFAULT);
    }

    private void mockNeighbors(CellPos center, double depth) {
        for (Direction dir : Direction.D8) {
            CellPos neighborPos = center.relative(dir);
            when(cache.getOrCompute(neighborPos)).thenReturn(cellAt(neighborPos, depth));
        }
    }

    private void mockNeighborsExcept(CellPos center, double defaultDepth, Direction exception, double exceptionDepth) {
        for (Direction dir : Direction.D8) {
            CellPos neighborPos = center.relative(dir);
            double depth = (dir == exception) ? exceptionDepth : defaultDepth;
            when(cache.getOrCompute(neighborPos)).thenReturn(cellAt(neighborPos, depth));
        }
    }
}
