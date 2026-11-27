package org.sosly.rivertale.cell.feature;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.terrain.CarveProposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConfluenceCarverTest {
    private final Cell join = cell(0, 0);
    private final Cell west = cell(-1, 0);
    private final Cell north = cell(0, -1);
    private final Cell south = cell(0, 1);
    private final Cell east = cell(1, 0);
    private final Watershed watershed = mock(Watershed.class);

    @Test
    void unequalInputsMeetOneOutgoingRunSizedByAccumulatedFlow() {
        inlet(west, 18);
        inlet(north, 1);
        when(watershed.upstream(join.pos())).thenReturn(Set.of(north.pos(), west.pos()));
        outlet(20);
        int mid = join.pos().getMiddleBlockX();
        int end = join.pos().getMaxBlockX();

        assertBed(0, mid, 78);
        assertBed(mid, 0, 79);
        assertTrue(!column(0, mid + 4).isEmpty());
        assertTrue(column(mid + 4, 0).isEmpty());
        assertBed(end, mid, 78);
        assertTrue(!column(end, mid + 4).isEmpty());
    }

    @Test
    void aThirdTributaryKeepsItsFlowAndDoesNotDuplicateTheOutgoingChannel() {
        inlet(west, 18);
        inlet(north, 1);
        inlet(south, 10);
        Set<CellPos> first = new LinkedHashSet<>(List.of(north.pos(), south.pos(), west.pos()));
        Set<CellPos> second = new LinkedHashSet<>(List.of(west.pos(), south.pos(), north.pos()));
        when(watershed.upstream(join.pos())).thenReturn(first);
        outlet(30);
        int mid = join.pos().getMiddleBlockX();
        int end = join.pos().getMaxBlockX();
        assertBed(mid, end, 78);
        assertBed(end, mid, 77);
        ChunkPos merge = new ChunkPos(mid >> 4, mid >> 4);
        List<CarveProposal> original = carve(merge);
        assertTrue(!original.isEmpty());
        when(watershed.upstream(join.pos())).thenReturn(second);
        assertEquals(original, carve(merge));
    }

    @Test
    void terminalJoinCarvesBothInputsButDoesNotContinueEast() {
        inlet(north, 1);
        inlet(west, 18);
        when(watershed.flow(join.pos())).thenReturn(OptionalDouble.of(20));
        when(watershed.upstream(join.pos())).thenReturn(Set.of(west.pos(), north.pos()));
        int mid = join.pos().getMiddleBlockX();
        assertBed(0, mid, 78);
        assertBed(mid, 0, 79);
        assertTrue(column(join.pos().getMaxBlockX(), mid).isEmpty());
    }

    private void inlet(Cell cell, double flow) {
        when(watershed.elevatedCell(cell.pos())).thenReturn(cell);
        when(watershed.flow(cell.pos())).thenReturn(OptionalDouble.of(flow));
    }

    private void outlet(double flow) {
        when(watershed.downstream(join.pos())).thenReturn(east.pos());
        when(watershed.elevatedCell(east.pos())).thenReturn(east);
        when(watershed.flow(join.pos())).thenReturn(OptionalDouble.of(flow));
    }

    private void assertBed(int x, int z, int y) {
        List<CarveProposal> proposals = column(x, z);
        assertEquals(1, proposals.size());
        assertEquals(y, Math.round(proposals.get(0).bedY()));
    }

    private List<CarveProposal> column(int x, int z) {
        return carve(new ChunkPos(x >> 4, z >> 4)).stream().filter(p -> p.x() == x && p.z() == z).toList();
    }

    private List<CarveProposal> carve(ChunkPos chunk) {
        return Feature.CONFLUENCE.handler.carve(join, watershed, chunk, 42);
    }

    private Cell cell(int x, int z) {
        return new Cell(new CellPos(x, z), Set.of()).withCellY(80);
    }
}
