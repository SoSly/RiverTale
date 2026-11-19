package org.sosly.rivertale.river;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionExplorer;
import org.sosly.rivertale.river.PreparedRiverData.Step;
import org.sosly.rivertale.world.RiverShaping;

public final class RiverPreparation {
    private final RiverGraph graph;
    private final Map<CellPos, Integer> reachable = new HashMap<>();

    private RiverPreparation(RiverGraph graph) {
        this.graph = graph;
    }

    public static PreparedRiverData prepare(RegionPos center, Set<RegionPos> visible, boolean local) {
        return prepare(center, visible, local, graph());
    }

    static PreparedRiverData prepare(RegionPos center, Set<RegionPos> visible, boolean local, RiverGraph graph) {
        Timer.Record timer = Store.getTimer(RiverPreparation.class, "prepare").start();
        try {
            RiverPreparation preparation = new RiverPreparation(graph);
            for (RegionPos pos : visible) {
                graph.region(pos).cells().forEach(graph::cell);
            }
            if (!local) {
                preparation.prepareConnected(preparation.prepareUpstreamRegions(center));
            }
            preparation.prepareSources(visible);
            return graph.finish();
        } finally {
            timer.stop();
        }
    }

    private static RiverGraph graph() {
        CommonConfig config = CommonConfig.get();
        return new RiverGraph(pos -> RegionCache.get().getOrCompute(pos),
            pos -> RegionExplorer.discover(pos.getWorldPosition(0)), RiverShaping::preclassify,
            config.fallbackApproachCells(), config.awayStepAllowance());
    }

    private Set<RegionPos> prepareUpstreamRegions(RegionPos center) {
        Set<RegionPos> found = new HashSet<>();
        ArrayDeque<RegionPos> pending = new ArrayDeque<>();
        found.add(center);
        pending.add(center);
        UpstreamSearch search = new UpstreamSearch(graph);
        while (!pending.isEmpty()) {
            RegionPos pos = pending.remove();
            for (RegionPos upstream : search.expand(graph.region(pos).cells())) {
                if (found.add(upstream)) {
                    pending.add(upstream);
                }
            }
        }
        return found;
    }

    private void prepareConnected(Set<RegionPos> roots) {
        Set<RegionPos> prepared = new HashSet<>();
        Set<RegionPos> pending = roots;
        UpstreamSearch search = new UpstreamSearch(graph);
        while (!pending.isEmpty()) {
            Set<RegionPos> batch = new HashSet<>(pending);
            batch.removeAll(prepared);
            if (batch.isEmpty()) {
                return;
            }
            prepared.addAll(batch);
            Set<CellPos> targets = prepareSources(batch);
            pending = search.expand(targets);
        }
    }

    private Set<CellPos> prepareSources(Collection<RegionPos> regions) {
        List<CellPos> sources = new ArrayList<>();
        for (RegionPos region : regions) {
            sources.addAll(graph.sources(region));
        }
        return expand(sources);
    }

    private Set<CellPos> expand(Collection<CellPos> roots) {
        ArrayDeque<Visit> pending = new ArrayDeque<>();
        roots.forEach(pos -> include(pos, 0, pending));
        Set<CellPos> targets = new HashSet<>();
        while (!pending.isEmpty()) {
            Visit current = pending.remove();
            if (reachable.get(current.pos()) < current.awaySteps()) {
                continue;
            }
            graph.cell(current.pos());
            targets.add(current.pos());
            for (Step step : graph.steps(current.pos())) {
                int away = step.approaching() ? 0 : current.awaySteps() + 1;
                if (away > graph.awayStepAllowance() || graph.isDivide(step.pos())) {
                    continue;
                }
                include(step.pos(), away, pending);
                addCrossingCorners(current.pos(), step.pos(), targets);
            }
        }
        return targets;
    }

    private void include(CellPos pos, int away, ArrayDeque<Visit> pending) {
        if (pos.getRegion().containsOutOfBoundsCells() || reachable.getOrDefault(pos, Integer.MAX_VALUE) <= away) {
            return;
        }
        reachable.put(pos, away);
        pending.add(new Visit(pos, away));
    }

    private void addCrossingCorners(CellPos from, CellPos to, Set<CellPos> targets) {
        if (from.x() == to.x() || from.z() == to.z()) {
            return;
        }
        targets.add(new CellPos(from.x(), to.z()));
        targets.add(new CellPos(to.x(), from.z()));
    }

    private record Visit(CellPos pos, int awaySteps) {
    }
}
