package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.PreparedRiverData.CellData;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MergedLakeTest {
    private final CellPos bottom = new CellPos(0, 0);
    private final CellPos rim = new CellPos(1, 0);
    private final CellPos pocket = new CellPos(0, 1);
    private final Map<CellPos, Integer> depths = Map.of(bottom, 5, rim, 0, pocket, 1);

    @Test
    void anOutletThroughAnotherRimCellDrainsTheWholeLakeInEitherTraceOrder() {
        Flowline closed = intoLake();
        CellPos out = new CellPos(2, 0);
        Flowline through = new Flowline(List.of(new CellPos(1, -1), rim, out), out, true);
        for (List<Flowline> paths : List.of(List.of(closed, through), List.of(through, closed))) {
            assertLake(paths, Feature.KETTLE);
        }
    }

    @Test
    void aSpillPrunedAtAnExistingTerminusIsNotAnOutlet() {
        CellPos out = new CellPos(0, -1);
        Flowline spill = new Flowline(List.of(new CellPos(2, 0), rim, bottom, out), out, true,
            List.of(new Flowline.Lake(Feature.KETTLE, depths)));
        Flowline stopped = new Flowline(List.of(new CellPos(-1, 0), bottom), bottom, true);
        assertLake(List.of(spill, stopped), Feature.ENDORHEIC);
    }

    private Flowline intoLake() {
        return new Flowline(List.of(new CellPos(-1, 0), bottom), bottom, true,
            List.of(new Flowline.Lake(Feature.ENDORHEIC, depths)));
    }

    private void assertLake(List<Flowline> paths, Feature expected) {
        Map<CellPos, CellData> cells = new HashMap<>();
        depths.forEach((pos, depth) -> cells.put(pos, new CellData(new Cell(pos, Set.of()), 74 - depth)));
        PreparedRiverData data = new PreparedRiverData(Map.of(), cells, Map.of(), Map.of(), Map.of(), Map.of(), 1, 63);
        PreparedRiverData result = RiverNetwork.withLakes(data, paths, WatershedBuilder.build(paths));
        depths.forEach((pos, depth) -> {
            assertEquals(expected, result.cell(pos).feature());
            assertEquals(depth.intValue(), result.cell(pos).cell().overFill());
        });
    }
}
