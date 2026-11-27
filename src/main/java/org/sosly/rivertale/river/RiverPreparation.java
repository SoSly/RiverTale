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
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionExplorer;
import org.sosly.rivertale.river.PreparedRiverData.Step;
import org.sosly.rivertale.world.RiverShaping;

public final class RiverPreparation {
    private final RiverGraph graph;
    private final Runnable checkCancelled;
    private final Set<CellPos> preparedWalls = new HashSet<>();
    private final Map<CellPos, Integer> reachable = new HashMap<>();

    private RiverPreparation(RiverGraph graph, Runnable checkCancelled) {
        this.graph = graph;
        this.checkCancelled = checkCancelled;
    }

    public static PreparedRiverData prepare(Set<RegionPos> roots) {
        return prepare(roots, graph());
    }

    static PreparedRiverData prepare(Set<RegionPos> roots, Runnable checkCancelled) {
        return prepare(roots, graph(), checkCancelled);
    }

    static PreparedRiverData prepare(Set<RegionPos> roots, RiverGraph graph) {
        return prepare(roots, graph, () -> { });
    }

    private static PreparedRiverData prepare(Set<RegionPos> roots, RiverGraph graph, Runnable checkCancelled) {
        Timer.Record timer = Store.getTimer(RiverPreparation.class, "prepare").start();
        try {
            RiverPreparation preparation = new RiverPreparation(graph, checkCancelled);
            for (RegionPos pos : roots) {
                checkCancelled.run();
                graph.region(pos).cells().forEach(graph::cell);
            }
            preparation.prepareConnected(preparation.prepareUpstreamRegions(roots));
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

    private Set<RegionPos> prepareUpstreamRegions(Set<RegionPos> roots) {
        Set<RegionPos> found = new HashSet<>(roots);
        ArrayDeque<RegionPos> pending = new ArrayDeque<>(roots);
        UpstreamSearch search = new UpstreamSearch(graph);
        while (!pending.isEmpty()) {
            checkCancelled.run();
            RegionPos pos = pending.remove();
            if (CommonConfig.get().maxLakeCells() > 0) {
                for (FlowDirection dir : FlowDirection.D4) {
                    RegionPos upstream = pos.relative(dir);
                    if (!upstream.containsOutOfBoundsCells() && !found.contains(upstream)
                        && graph.routing(upstream).allowedRegions().contains(pos)) {
                        found.add(upstream);
                        pending.add(upstream);
                    }
                }
            }
            for (RegionPos upstream : search.expand(graph.region(pos).cells(), checkCancelled)) {
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
            pending = search.expand(targets, checkCancelled);
        }
    }

    private Set<CellPos> prepareSources(Collection<RegionPos> regions) {
        List<CellPos> sources = new ArrayList<>();
        for (RegionPos region : regions) {
            checkCancelled.run();
            sources.addAll(graph.sources(region));
        }
        return expand(sources);
    }

    private Set<CellPos> expand(Collection<CellPos> roots) {
        ArrayDeque<Visit> pending = new ArrayDeque<>();
        roots.forEach(pos -> include(pos, 0, pending));
        Set<CellPos> targets = new HashSet<>();
        while (!pending.isEmpty()) {
            checkCancelled.run();
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
                if (graph.entersDepression(current.pos(), step.pos())) {
                    prepareWalls(step.pos(), pending, targets);
                }
                prepareLake(current.pos(), step, pending, targets);
                addCrossingCorners(current.pos(), step.pos(), targets);
            }
        }
        return targets;
    }

    private void prepareWalls(CellPos entry, ArrayDeque<Visit> pending, Set<CellPos> targets) {
        ArrayDeque<CellPos> front = new ArrayDeque<>();
        front.add(entry);
        while (!front.isEmpty()) {
            checkCancelled.run();
            CellPos from = front.remove();
            if (!preparedWalls.add(from)) {
                continue;
            }
            for (Step wall : graph.wallSteps(from)) {
                include(wall.pos(), 0, pending);
                addCrossingCorners(from, wall.pos(), targets);
                front.add(wall.pos());
            }
        }
    }

    private void prepareLake(CellPos from, Step step, ArrayDeque<Visit> pending, Set<CellPos> targets) {
        LakePlan plan = graph.lake(from, step);
        if (plan == null) {
            return;
        }
        targets.addAll(plan.closed().keySet());
        for (LakeFill.Spill spill : plan.spills()) {
            targets.addAll(spill.overFill().keySet());
            List<CellPos> route = spill.route();
            for (int i = 1; i < route.size(); i++) {
                targets.add(route.get(i));
                addCrossingCorners(route.get(i - 1), route.get(i), targets);
            }
            include(spill.lip(), 0, pending);
        }
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
