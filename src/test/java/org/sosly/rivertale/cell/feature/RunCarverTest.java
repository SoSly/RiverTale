package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.CarveProposal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RunCarverTest {
    @Test
    void largerFlowsMakeADeeperAndWiderCutWithAParabolicBed() {
        ChunkPos chunk = new ChunkPos(0, 0);
        RiverSpline straight = t -> new Vec3(16 * t, 80, 8.5);
        List<CarveProposal> low = CourseCarver.carve(List.of(straight), 1, 1, chunk, 16);
        List<CarveProposal> high = CourseCarver.carve(List.of(straight), 24, 24, chunk, 16);

        assertTrue(high.size() > low.size());
        assertEquals(79, at(low, 8, 8).bedY(), 1e-9);
        double deep = 2;
        double factor = 1 - Math.pow(4 / 8.5, 2);
        assertEquals(80 - deep, at(high, 8, 8).bedY(), 1e-9);
        assertEquals(80 - deep * factor, at(high, 8, 12).bedY(), 1e-9);
        assertEquals(at(high, 8, 4).bedY(), at(high, 8, 12).bedY(), 1e-9);
    }

    @Test
    void flowEighteenProducesABroadTwoBlockBottom() {
        RiverSpline straight = t -> new Vec3(16 * t, 80, 7.5);
        List<CarveProposal> proposals = CourseCarver.carve(List.of(straight), 18, 18, new ChunkPos(0, 0), 16);
        int[] depths = new int[16];
        for (CarveProposal proposal : proposals) {
            if (proposal.x() == 8) {
                depths[proposal.z()] = 80 - (int) Math.round(proposal.bedY());
            }
        }
        assertArrayEquals(new int[]{0, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 1, 1, 1, 0, 0}, depths);
    }

    @Test
    void connectedRunsUseTheSameFlowAndElevationAtTheirSharedCrossing() {
        Cell upstream = cell(-1, 0);
        Cell middle = cell(0, 0);
        Cell downstream = cell(1, 0);
        Cell outlet = cell(2, 0);
        Watershed watershed = mock(Watershed.class);
        link(watershed, upstream, middle, downstream, 1);
        link(watershed, middle, downstream, outlet, 24);
        when(watershed.flow(downstream.pos())).thenReturn(OptionalDouble.of(48));
        int size = CommonConfig.get().cellBlocks();
        ChunkPos chunkAtJoin = new ChunkPos(size / 16, size / 32);

        List<CarveProposal> ending = Feature.RUN.handler.carve(middle, watershed, chunkAtJoin, 42);
        List<CarveProposal> starting = Feature.RUN.handler.carve(downstream, watershed, chunkAtJoin, 42);
        double expectedBed = 80 - 2 * (1 - 0.5 / (8.5 * 8.5));
        assertEquals(expectedBed, at(ending, size, size / 2).bedY(), 1e-9);
        ChunkPos beforeJoin = new ChunkPos(chunkAtJoin.x - 1, chunkAtJoin.z);
        List<CarveProposal> entering = Feature.RUN.handler.carve(downstream, watershed, beforeJoin, 42);
        assertEquals(expectedBed, at(entering, size - 1, size / 2).bedY(), 1e-9);
        assertTrue(Math.abs(at(ending, size, size / 2).bedY() - at(starting, size, size / 2).bedY()) < 0.1);
    }

    private void link(Watershed watershed, Cell from, Cell to, Cell next, double flow) {
        when(watershed.upstream(to.pos())).thenReturn(Set.of(from.pos()));
        when(watershed.downstream(to.pos())).thenReturn(next.pos());
        when(watershed.elevatedCell(from.pos())).thenReturn(from);
        when(watershed.elevatedCell(next.pos())).thenReturn(next);
        when(watershed.flow(from.pos())).thenReturn(OptionalDouble.of(flow));
    }

    private CarveProposal at(List<CarveProposal> proposals, int x, int z) {
        return proposals.stream().filter(p -> p.x() == x && p.z() == z).findFirst().orElseThrow();
    }

    private Cell cell(int x, int z) {
        return new Cell(new CellPos(x, z), Set.of()).withCellY(80);
    }
}
