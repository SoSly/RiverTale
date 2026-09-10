package org.sosly.rivertale.river;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;

final class UpstreamSearch {
    private final FlowlineTracer tracer;
    private final Map<CellPos, Integer> visited = new HashMap<>();
    private final Set<RegionPos> regions = new HashSet<>();

    UpstreamSearch(FlowlineTracer tracer) {
        this.tracer = tracer;
    }

    Set<RegionPos> expand(Collection<CellPos> targets) {
        ArrayDeque<Visit> pending = new ArrayDeque<>();
        for (CellPos target : targets) {
            include(target, 0, pending);
        }
        Set<RegionPos> discovered = new HashSet<>();
        while (!pending.isEmpty()) {
            Visit current = pending.remove();
            if (visited.get(current.pos()) < current.awaySteps()) {
                continue;
            }
            if (regions.add(current.pos().getRegion())) {
                discovered.add(current.pos().getRegion());
            }
            for (CellPos previous : tracer.predecessors(current.pos())) {
                int awaySteps = tracer.movesAway(previous, current.pos()) ? current.awaySteps() + 1 : 0;
                if (awaySteps <= tracer.awayStepAllowance()) {
                    include(previous, awaySteps, pending);
                }
            }
        }
        return discovered;
    }

    private void include(CellPos cell, int awaySteps, ArrayDeque<Visit> pending) {
        if (cell.getRegion().containsOutOfBoundsCells() || visited.getOrDefault(cell, Integer.MAX_VALUE) <= awaySteps) {
            return;
        }
        visited.put(cell, awaySteps);
        pending.add(new Visit(cell, awaySteps));
    }

    int regionCount() {
        return regions.size();
    }

    int cellCount() {
        return visited.size();
    }

    private record Visit(CellPos pos, int awaySteps) {
    }
}
