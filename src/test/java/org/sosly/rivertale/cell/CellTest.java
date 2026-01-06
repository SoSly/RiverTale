package org.sosly.rivertale.cell;

import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;


import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CellTest {

    @Mock
    SampleCache sampleCache;

    @Test
    void computeFlowDirectionsReturnsEmptyWhenFlat() {
        CellPos pos = new CellPos(0, 0);
        Sample sample = sampleAt(pos, 0.5);
        mockNeighbors(pos, 0.5);

        List<Direction> flow = Cell.computeFlowDirections(pos, sample, sampleCache);

        assertTrue(flow.isEmpty());
    }

    @Test
    void computeFlowDirectionsReturnsNorthFirstWhenNorthIsLowest() {
        CellPos pos = new CellPos(0, 0);
        Sample sample = sampleAt(pos, 0.5);
        mockNeighborsExcept(pos, 0.5, Direction.NORTH, 0.2);

        List<Direction> flow = Cell.computeFlowDirections(pos, sample, sampleCache);

        assertEquals(Direction.NORTH, flow.get(0));
    }

    @Test
    void computeFlowDirectionsReturnsSouthFirstWhenSouthIsLowest() {
        CellPos pos = new CellPos(0, 0);
        Sample sample = sampleAt(pos, 0.5);
        mockNeighborsExcept(pos, 0.5, Direction.SOUTH, 0.2);

        List<Direction> flow = Cell.computeFlowDirections(pos, sample, sampleCache);

        assertEquals(Direction.SOUTH, flow.get(0));
    }

    @Test
    void computeFlowDirectionsReturnsEastFirstWhenEastIsLowest() {
        CellPos pos = new CellPos(0, 0);
        Sample sample = sampleAt(pos, 0.5);
        mockNeighborsExcept(pos, 0.5, Direction.EAST, 0.2);

        List<Direction> flow = Cell.computeFlowDirections(pos, sample, sampleCache);

        assertEquals(Direction.EAST, flow.get(0));
    }

    @Test
    void computeFlowDirectionsReturnsWestFirstWhenWestIsLowest() {
        CellPos pos = new CellPos(0, 0);
        Sample sample = sampleAt(pos, 0.5);
        mockNeighborsExcept(pos, 0.5, Direction.WEST, 0.2);

        List<Direction> flow = Cell.computeFlowDirections(pos, sample, sampleCache);

        assertEquals(Direction.WEST, flow.get(0));
    }

    @Test
    void computeFlowDirectionsReturnsDiagonalFirstWhenDiagonalIsLowest() {
        CellPos pos = new CellPos(0, 0);
        Sample sample = sampleAt(pos, 0.5);
        mockNeighborsExcept(pos, 0.5, Direction.NORTHEAST, 0.0);

        List<Direction> flow = Cell.computeFlowDirections(pos, sample, sampleCache);

        assertEquals(Direction.NORTHEAST, flow.get(0));
    }

    @Test
    void computeFlowDirectionsPrefersCardinalOverDiagonalWhenSlopeEqual() {
        CellPos pos = new CellPos(0, 0);
        Sample sample = sampleAt(pos, 1.0);

        for (Direction dir : Direction.D8) {
            CellPos neighborPos = pos.relative(dir);
            double depth = (dir.dx != 0 && dir.dz != 0) ? 1.0 - 0.3 * Math.sqrt(2) : 0.7;
            mockSampleAt(neighborPos, depth);
        }

        List<Direction> flow = Cell.computeFlowDirections(pos, sample, sampleCache);

        assertTrue(flow.get(0).dx == 0 || flow.get(0).dz == 0);
    }

    @Test
    void computeFlowDirectionsReturnsEmptyWhenUphillEverywhere() {
        CellPos pos = new CellPos(0, 0);
        Sample sample = sampleAt(pos, 0.2);
        mockNeighbors(pos, 0.5);

        List<Direction> flow = Cell.computeFlowDirections(pos, sample, sampleCache);

        assertTrue(flow.isEmpty());
    }

    @Test
    void computeFlowDirectionsReturnsSteepestFirstWhenMultipleDownhill() {
        CellPos pos = new CellPos(0, 0);
        Sample sample = sampleAt(pos, 1.0);

        mockSampleAt(pos.relative(Direction.NORTH), 0.8);
        mockSampleAt(pos.relative(Direction.SOUTH), 0.3);
        mockSampleAt(pos.relative(Direction.EAST), 0.9);
        mockSampleAt(pos.relative(Direction.WEST), 0.9);
        for (Direction dir : new Direction[]{Direction.NORTHEAST, Direction.NORTHWEST, Direction.SOUTHEAST, Direction.SOUTHWEST}) {
            mockSampleAt(pos.relative(dir), 0.9);
        }

        List<Direction> flow = Cell.computeFlowDirections(pos, sample, sampleCache);

        assertEquals(8, flow.size());
        assertEquals(Direction.SOUTH, flow.get(0));
        assertEquals(Direction.NORTH, flow.get(1));
    }

    @Test
    void hasUpstreamNeighborReturnsFalseWhenNullCache() {
        CellPos pos = new CellPos(0, 0);

        boolean result = Cell.hasUpstreamNeighbor(pos, null);

        assertEquals(false, result);
    }

    @Test
    void hasUpstreamNeighborReturnsFalseWhenNoNeighborFlowsIn() {
        CellPos pos = new CellPos(0, 0);
        mockSampleAt(pos, 0.0);
        for (Direction dir : Direction.D8) {
            CellPos neighbor = pos.relative(dir);
            mockSampleAt(neighbor, 1.0);
            mockNeighborsOf(neighbor, 1.0);
        }

        boolean result = Cell.hasUpstreamNeighbor(pos, sampleCache);

        assertEquals(false, result);
    }

    @Test
    void hasUpstreamNeighborReturnsTrueWhenOneNeighborFlowsIn() {
        CellPos pos = new CellPos(0, 0);
        CellPos north = pos.relative(Direction.NORTH);

        mockSampleAt(pos, 0.0);
        mockSampleAt(north, 1.0);

        for (Direction dir : Direction.D8) {
            mockSampleAt(north.relative(dir), dir == Direction.SOUTH ? 0.0 : 1.0);
        }

        for (Direction dir : Direction.D8) {
            if (dir == Direction.NORTH) {
                continue;
            }
            CellPos neighbor = pos.relative(dir);
            mockSampleAt(neighbor, 1.0);
            for (Direction neighborDir : Direction.D8) {
                CellPos nn = neighbor.relative(neighborDir);
                if (!nn.equals(pos) && !nn.equals(north)) {
                    mockSampleAt(nn, 1.0);
                }
            }
        }

        boolean result = Cell.hasUpstreamNeighbor(pos, sampleCache);

        assertEquals(true, result);
    }

    @Test
    void hasUpstreamNeighborReturnsFalseWhenAllNeighborsFlat() {
        CellPos pos = new CellPos(0, 0);
        mockSampleAt(pos, 0.5);
        for (Direction dir : Direction.D8) {
            CellPos neighbor = pos.relative(dir);
            mockSampleAt(neighbor, 0.5);
            mockNeighborsOf(neighbor, 0.5);
        }

        boolean result = Cell.hasUpstreamNeighbor(pos, sampleCache);

        assertEquals(false, result);
    }

    private Sample sampleAt(CellPos pos, double depth) {
        ChunkPos chunk = new ChunkPos(pos.getMiddleBlockX() >> 4, pos.getMiddleBlockZ() >> 4);
        return new Sample(chunk, 0.5, depth, 0, 0, 0, 0);
    }

    private void mockSampleAt(CellPos pos, double depth) {
        int x = pos.getMiddleBlockX();
        int z = pos.getMiddleBlockZ();
        when(sampleCache.getOrCompute(x, z)).thenReturn(sampleAt(pos, depth));
    }

    private void mockNeighbors(CellPos center, double depth) {
        for (Direction dir : Direction.D8) {
            CellPos neighborPos = center.relative(dir);
            mockSampleAt(neighborPos, depth);
        }
    }

    private void mockNeighborsExcept(CellPos center, double defaultDepth, Direction exception, double exceptionDepth) {
        for (Direction dir : Direction.D8) {
            CellPos neighborPos = center.relative(dir);
            double depth = (dir == exception) ? exceptionDepth : defaultDepth;
            mockSampleAt(neighborPos, depth);
        }
    }

    private void mockNeighborsOf(CellPos center, double depth) {
        for (Direction dir : Direction.D8) {
            CellPos neighborPos = center.relative(dir);
            mockSampleAt(neighborPos, depth);
        }
    }
}
