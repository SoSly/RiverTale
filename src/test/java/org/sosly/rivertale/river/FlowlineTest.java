package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.core.CellPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlowlineTest {
    @Test
    void returnedPathCannotBeChangedThroughItsInputOrAccessor() {
        CellPos source = new CellPos(1, 2);
        List<CellPos> positions = new ArrayList<>(List.of(source));
        Flowline path = new Flowline(positions, source, true);
        positions.clear();

        assertEquals(List.of(source), path.cells());
        assertThrows(UnsupportedOperationException.class, () -> path.cells().clear());
    }

    @Test
    void lengthCountsCardinalAndDiagonalSegmentsInsteadOfStraightLineDistance() {
        CellPos source = new CellPos(-2, -2);
        CellPos terminus = new CellPos(-1, -1);
        Flowline path = new Flowline(List.of(source, new CellPos(-1, -2), new CellPos(0, -1), terminus), terminus, true);

        assertEquals(128 + 64 * Math.sqrt(2), path.lengthBlocks(64), 0.000001);
        assertEquals(32 + 16 * Math.sqrt(2), path.lengthBlocks(16), 0.000001);
        assertEquals(0, new Flowline(List.of(source), source, true).lengthBlocks(64));
    }
}
