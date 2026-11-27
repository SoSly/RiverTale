package org.sosly.rivertale.river;

import java.util.List;
import java.util.Map;
import org.sosly.rivertale.core.CellPos;

record LakePlan(List<LakeFill.Spill> spills, Map<CellPos, Integer> closed) {
    LakePlan {
        spills = List.copyOf(spills);
        closed = Map.copyOf(closed);
    }

    record Inlet(CellPos from, CellPos entry) {
    }
}
