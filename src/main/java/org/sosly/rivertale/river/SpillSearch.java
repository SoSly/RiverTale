package org.sosly.rivertale.river;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.PreparedRiverData.Step;

/** Identifies downstream searches that see the same barriers, regardless of how the river arrived. */
final class SpillSearch {
    private final PreparedRiverData data;
    private final Map<CellPos, List<LakePlan>> lakes = new HashMap<>();

    SpillSearch(PreparedRiverData data) {
        this.data = data;
        data.lakes().forEach((inlet, plan) -> lakes.computeIfAbsent(inlet.entry(), key -> new ArrayList<>()).add(plan));
    }

    Outlet outlet(CellPos lip, Set<CellPos> excluded) {
        Set<CellPos> barriers = new HashSet<>();
        Set<CellPos> visited = new HashSet<>();
        ArrayDeque<CellPos> pending = new ArrayDeque<>();
        visited.add(lip);
        pending.add(lip);
        boolean shore = false;
        while (!pending.isEmpty()) {
            CellPos pos = pending.remove();
            if (data.routing(pos.getRegion()).terminusCells().contains(pos)) {
                shore = true;
                continue;
            }
            for (Step step : data.moves().getOrDefault(pos, List.of())) {
                if (!data.moves().containsKey(step.pos()) || data.cell(step.pos()).feature() == Feature.DIVIDE
                    || (pos.equals(lip) && data.cell(step.pos()).height() >= data.cell(lip).height())) {
                    continue;
                }
                if (excluded.contains(step.pos())) {
                    barriers.add(step.pos());
                } else if (visited.add(step.pos())) {
                    pending.add(step.pos());
                }
            }
            for (LakePlan lake : lakes.getOrDefault(pos, List.of())) {
                for (CellPos member : lake.closed().keySet()) {
                    if (excluded.contains(member)) {
                        barriers.add(member);
                    }
                }
                for (LakeFill.Spill spill : lake.spills()) {
                    boolean blocked = false;
                    for (CellPos member : spill.overFill().keySet()) {
                        if (excluded.contains(member)) {
                            blocked = true;
                            barriers.add(member);
                        }
                    }
                    if (excluded.contains(spill.lip())) {
                        barriers.add(spill.lip());
                    } else if (!blocked && visited.add(spill.lip())) {
                        pending.add(spill.lip());
                    }
                }
            }
        }
        return new Outlet(lip, Set.copyOf(barriers), shore);
    }

    record Outlet(CellPos lip, Set<CellPos> barriers, boolean possibleShore) {
    }
}
