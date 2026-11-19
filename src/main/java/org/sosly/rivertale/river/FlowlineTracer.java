package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Distribution;
import org.sosly.rivertale.metric.Ratio;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.river.PreparedRiverData.Routing;
import org.sosly.rivertale.river.PreparedRiverData.Step;

public class FlowlineTracer {
    private final PreparedRiverData data;
    private final int awayStepAllowance;
    private final Map<Arrival, Step> outlets = new ConcurrentHashMap<>();

    public FlowlineTracer(PreparedRiverData data) {
        this.data = data;
        this.awayStepAllowance = data.awayStepAllowance();
    }

    PreparedRiverData data() {
        return data;
    }

    Region region(RegionPos pos) {
        return data.region(pos);
    }

    List<CellPos> sources(RegionPos pos) {
        return data.sources(pos);
    }

    List<CellPos> predecessors(CellPos pos) {
        return data.predecessors(pos);
    }

    int awayStepAllowance() {
        return awayStepAllowance;
    }

    boolean movesAway(CellPos from, CellPos to) {
        return steps(from).stream().anyMatch(step -> step.pos().equals(to) && !step.approaching());
    }

    boolean hasDestinations(RegionPos pos) {
        return !routing(pos).termini().isEmpty();
    }

    private Routing routing(RegionPos pos) {
        return data.routing(pos);
    }

    private List<Step> steps(CellPos pos) {
        return data.steps(pos);
    }

    private boolean isDivide(CellPos pos) {
        return data.cell(pos).feature() == Feature.DIVIDE;
    }

    private boolean isTerminus(CellPos pos) {
        return routing(pos.getRegion()).terminusCells().contains(pos);
    }

    public Flowline trace(CellPos source) {
        Timer.Record timer = Store.getTimer(FlowlineTracer.class, "trace").start();
        try {
            Flowline path = traceSource(source);
            Ratio successes = Store.getRatio(FlowlineTracer.class, "success");
            if (path.isValid()) {
                successes.success();
                recordPathMetrics(path);
            } else {
                successes.failure();
            }
            return path;
        } finally {
            timer.stop();
        }
    }

    private void recordPathMetrics(Flowline path) {
        if (!CommonConfig.get().enableMetrics()) {
            return;
        }
        Store.getDistribution(FlowlineTracer.class, "lengthBlocks").record(path.lengthBlocks(CommonConfig.get().cellBlocks()));
        int[] heights = path.cells().stream().mapToInt(pos -> data.cell(pos).height()).toArray();
        int outlet = data.seaLevel();
        Store.getDistribution(FlowlineTracer.class, "netFallBlocks").record((long) heights[0] - outlet);
        if (heights.length < 2) {
            return;
        }
        Distribution sampled = Store.getDistribution(FlowlineTracer.class, "cellFallBlocks");
        FlowlineProfile.sampledFalls(heights, outlet).forEach(sampled::record);
        recordCarvedFalls("carveFlat", FlowlineProfile.carvedFalls(heights, outlet, 0));
        recordCarvedFalls("carveDescending", FlowlineProfile.carvedFalls(heights, outlet, 1));
    }

    private void recordCarvedFalls(String name, Optional<List<Long>> falls) {
        Ratio feasible = Store.getRatio(FlowlineTracer.class, name + "Feasible");
        if (falls.isEmpty()) {
            feasible.failure();
            return;
        }
        feasible.success();
        Distribution distribution = Store.getDistribution(FlowlineTracer.class, name + "FallBlocks");
        falls.get().forEach(distribution::record);
    }

    private Flowline traceSource(CellPos source) {
        Routing local = routing(source.getRegion());
        if (!local.allows(source) || local.termini().isEmpty()) {
            return new Flowline(List.of(source), null, false);
        }
        if (isTerminus(source)) {
            return new Flowline(List.of(source), source, true);
        }
        return new Notes(source).find();
    }

    private void recordChosen(List<Step> chosen, List<Integer> awayAtCell) {
        if (!CommonConfig.get().enableMetrics()) {
            return;
        }
        long total = 0;
        Distribution rises = Store.getDistribution(FlowlineTracer.class, "uphillNudgeBlocks");
        for (Step step : chosen) {
            if (step.nudgingRise() > 0) {
                total += step.nudgingRise();
                rises.record(step.nudgingRise());
            }
        }
        Store.getDistribution(FlowlineTracer.class, "nudgingRiseBlocks").record(total);
        int awayTotal = 0;
        int longestRun = 0;
        for (int away : awayAtCell) {
            if (away > 0) {
                awayTotal++;
                longestRun = Math.max(longestRun, away);
            }
        }
        Store.getDistribution(FlowlineTracer.class, "awaySteps").record(awayTotal);
        Store.getDistribution(FlowlineTracer.class, "maxConsecutiveAwaySteps").record(longestRun);
    }

    private record Arrival(CellPos cell, int awaySteps) {
    }

    private final class Notes {
        private final CellPos source;
        private final Map<Arrival, Arrival> parents = new HashMap<>();
        private final Map<Arrival, Step> taken = new HashMap<>();
        private final Set<Arrival> expanded = new HashSet<>();
        private final PriorityQueue<Node> pending = new PriorityQueue<>(FlowlineTracer::byPreference);
        private int order;

        private Notes(CellPos source) {
            this.source = source;
        }

        private Flowline find() {
            Arrival start = new Arrival(source, 0);
            List<CellPos> stored = follow(start, new HashSet<>());
            if (stored != null) {
                return finish(stored, stepsBetween(stored));
            }
            pending.add(new Node(start, EMPTY_CHOICES, order++, null, null));
            while (!pending.isEmpty()) {
                Node current = pending.poll();
                if (!expanded.add(current.arrival)) {
                    continue;
                }
                if (current.step != null) {
                    parents.put(current.arrival, current.parent);
                    taken.put(current.arrival, current.step);
                }
                if (isTerminus(current.arrival.cell)) {
                    List<CellPos> cells = path(current.arrival, List.of());
                    publish(cells);
                    return finish(cells, stepsBetween(cells));
                }
                Set<CellPos> ancestors = used(current.arrival);
                ancestors.remove(current.arrival.cell);
                List<CellPos> joined = follow(current.arrival, ancestors);
                if (joined != null && joined.size() > 1) {
                    List<CellPos> cells = path(current.arrival, joined.subList(1, joined.size()));
                    publish(cells);
                    return finish(cells, stepsBetween(cells));
                }
                List<Step> candidates = steps(current.arrival.cell);
                for (int index = 0; index < candidates.size(); index++) {
                    Step step = candidates.get(index);
                    int away = step.approaching() ? 0 : current.arrival.awaySteps + 1;
                    if (away > awayStepAllowance || isDivide(step.pos()) || used(current.arrival).contains(step.pos())) {
                        continue;
                    }
                    Arrival next = new Arrival(step.pos(), away);
                    if (expanded.contains(next)) {
                        continue;
                    }
                    pending.add(new Node(next, extend(current.choices, index), order++, current.arrival, step));
                }
            }
            return new Flowline(List.of(source), null, false);
        }

        private Flowline finish(List<CellPos> cells, List<Step> chosen) {
            List<Integer> away = new ArrayList<>();
            away.add(0);
            for (Step step : chosen) {
                int previous = away.get(away.size() - 1);
                away.add(step.approaching() ? 0 : previous + 1);
            }
            recordChosen(chosen, away);
            return new Flowline(cells, cells.get(cells.size() - 1), true);
        }

        private void publish(List<CellPos> cells) {
            List<Step> chosen = stepsBetween(cells);
            int away = 0;
            for (int i = 0; i < chosen.size(); i++) {
                outlets.putIfAbsent(new Arrival(cells.get(i), away), chosen.get(i));
                away = chosen.get(i).approaching() ? 0 : away + 1;
            }
        }

        private List<Step> stepsBetween(List<CellPos> cells) {
            List<Step> chosen = new ArrayList<>();
            for (int i = 0; i < cells.size() - 1; i++) {
                CellPos from = cells.get(i);
                CellPos to = cells.get(i + 1);
                Step match = null;
                for (Step step : steps(from)) {
                    if (step.pos().equals(to)) {
                        match = step;
                        break;
                    }
                }
                if (match == null) {
                    throw new IllegalStateException("Stored river step is no longer legal at " + from);
                }
                chosen.add(match);
            }
            return chosen;
        }

        private List<CellPos> path(Arrival end, List<CellPos> tail) {
            List<CellPos> cells = new ArrayList<>();
            Arrival cursor = end;
            while (cursor != null) {
                cells.add(cursor.cell);
                cursor = parents.get(cursor);
            }
            java.util.Collections.reverse(cells);
            cells.addAll(tail);
            return cells;
        }

        private Set<CellPos> used(Arrival end) {
            Set<CellPos> cells = new HashSet<>();
            Arrival cursor = end;
            while (cursor != null) {
                cells.add(cursor.cell);
                cursor = parents.get(cursor);
            }
            return cells;
        }

        private List<CellPos> follow(Arrival start, Set<CellPos> used) {
            if (!outlets.containsKey(start) && !isTerminus(start.cell)) {
                return null;
            }
            List<CellPos> tail = new ArrayList<>();
            Arrival cursor = start;
            while (cursor != null) {
                if (!used.add(cursor.cell)) {
                    return null;
                }
                tail.add(cursor.cell);
                if (isTerminus(cursor.cell)) {
                    return tail;
                }
                Step step = outlets.get(cursor);
                if (step == null) {
                    return null;
                }
                int away = step.approaching() ? 0 : cursor.awaySteps + 1;
                if (away > awayStepAllowance || isDivide(step.pos())) {
                    return null;
                }
                cursor = new Arrival(step.pos(), away);
            }
            return null;
        }
    }

    private static final int[] EMPTY_CHOICES = new int[0];

    private static int[] extend(int[] prefix, int index) {
        int[] choices = new int[prefix.length + 1];
        System.arraycopy(prefix, 0, choices, 0, prefix.length);
        choices[prefix.length] = index;
        return choices;
    }

    private static int byPreference(Node left, Node right) {
        int length = Math.min(left.choices.length, right.choices.length);
        for (int i = 0; i < length; i++) {
            if (left.choices[i] != right.choices[i]) {
                return Integer.compare(left.choices[i], right.choices[i]);
            }
        }
        int byLength = Integer.compare(left.choices.length, right.choices.length);
        if (byLength != 0) {
            return byLength;
        }
        return Integer.compare(left.order, right.order);
    }

    private record Node(Arrival arrival, int[] choices, int order, Arrival parent, Step step) {
    }
}
