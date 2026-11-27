package org.sosly.rivertale.river;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LakeFillTest {
    private final CellPos entry = new CellPos(3, 1);
    private final CellPos pocket = new CellPos(3, 0);
    private final CellPos lip = new CellPos(4, 1);
    private final Map<CellPos, Integer> heights = new HashMap<>(Map.of(entry, 119, pocket, 121, lip, 123));
    private final LakeFill fill = new LakeFill(pos -> Arrays.stream(FlowDirection.D8).map(pos::relative).toList(),
        pos -> heights.getOrDefault(pos, 131));

    @Test
    void sharedSurfaceSpillsDirectlyFromEntryAndRetainsSidePocketDepth() {
        LakeFill.Spill spill = fill.search(entry, 128, 4, end -> end.lip().equals(lip)).orElseThrow();
        assertEquals(List.of(entry, lip), spill.route());
        assertEquals(Map.of(entry, 4, pocket, 2), spill.overFill());
        Cell cell = new Cell(entry, Set.of()).withCellY(117).withOverFill(spill.overFill().get(entry));
        assertEquals(117, cell.cellY().orElseThrow());
        assertEquals(4, cell.overFill());
    }

    @Test
    void poolCanAbsorbTheIncomingCellBeforeSpillingAcrossItsFarRim() {
        CellPos incoming = new CellPos(2, 2);
        CellPos farLip = new CellPos(1, 2);
        heights.remove(lip);
        heights.put(incoming, 130);
        LakeFill.Spill spill = fill.search(entry, 131, 4, end -> end.lip().equals(farLip)).orElseThrow();
        assertEquals(List.of(entry, incoming, farLip), spill.route());
        assertEquals(Map.of(entry, 12, pocket, 10, incoming, 1), spill.overFill());
    }

    @Test
    void backtrackCeilingIsInclusive() {
        heights.put(lip, 128);
        assertEquals(9, fill.search(entry, 128, 4, end -> end.lip().equals(lip)).orElseThrow().overFill().get(entry));
        heights.put(lip, 129);
        assertTrue(fill.search(entry, 128, 4, end -> end.lip().equals(lip)).isEmpty());
    }

    @Test
    void fourLakeMembersCanSpillButAFifthCancelsTheAttempt() {
        int cap = CommonConfig.get().maxLakeCells();
        assertEquals(4, cap);
        heights.put(new CellPos(2, 1), 120);
        heights.put(new CellPos(2, 0), 120);
        assertEquals(4, fill.search(entry, 128, cap, end -> end.lip().equals(lip)).orElseThrow().overFill().size());
        heights.put(new CellPos(2, 2), 122);
        assertTrue(fill.search(entry, 128, cap, end -> end.lip().equals(lip)).isEmpty());
    }
}
