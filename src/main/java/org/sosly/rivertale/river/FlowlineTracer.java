package org.sosly.rivertale.river;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    private final int maxBacktrack = CommonConfig.get().maxBacktrackCells();
    private final int maxBacktrackRise = CommonConfig.get().maxBacktrackRise();
    private final SpillSearch spillSearch;
    private final Map<Arrival, Step> outlets = new ConcurrentHashMap<>();

    public FlowlineTracer(PreparedRiverData data) {
        this.data = data;
        this.spillSearch = new SpillSearch(data);
        this.awayStepAllowance = data.awayStepAllowance();
    }

    PreparedRiverData data() {
        return data;
    }

    Region region(RegionPos pos) {
        return data.region(pos);
    }

    List<RegionPos> preparedSourceRegions() {
        return data.sources().entrySet().stream().filter(entry -> !entry.getValue().isEmpty())
            .map(Map.Entry::getKey).toList();
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
        return new LakeRoutes().find(source);
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

    private Arrival advance(Arrival from, Step step) {
        int away = step.approaching() ? 0 : from.awaySteps + 1;
        int change = data.cell(step.pos()).height() - data.cell(from.cell).height();
        boolean wall = change < 0 ? data.cell(step.pos()).flowDirections().isEmpty()
            : from.wall && step.approaching() && change <= CommonConfig.get().nudgingLimit();
        return new Arrival(step.pos(), away, wall);
    }

    private record Arrival(CellPos cell, int awaySteps, boolean wall) {
    }

    private final class Notes {
        private final CellPos source;
        private final Map<Arrival, Arrival> parents = new HashMap<>();
        private final Map<Arrival, Step> taken = new HashMap<>();
        private final Set<Arrival> expanded = new HashSet<>();
        private final PriorityQueue<Node> pending = new PriorityQueue<>(FlowlineTracer::byPreference);
        private int order;
        private int[] committed = EMPTY_CHOICES;
        private int committedDepth;
        private int furthest;
        private Node tip;
        private long returnCeiling;
        private Node lake;
        private final Set<CellPos> blocked;

        private Notes(CellPos source) {
            this(source, Set.of());
        }

        private Notes(CellPos source, Set<CellPos> blocked) {
            this.source = source;
            this.blocked = blocked;
            pending.add(new Node(new Arrival(source, 0, false), EMPTY_CHOICES, order++, null, null));
        }

        private Flowline advanceToLakeOrOutlet() {
            Arrival start = new Arrival(source, 0, false);
            lake = null;
            while (!pending.isEmpty()) {
                Node current = pending.remove();
                if (!withinWindow(current.choices) || !canSwitchTo(current)) {
                    continue;
                }
                if (current.lake != null) {
                    lake = current;
                    return null;
                }
                if (!expanded.add(current.arrival)) {
                    continue;
                }
                tip = current;
                returnCeiling = (long) data.cell(current.arrival.cell).height() + maxBacktrackRise;
                advanceWindow(current.choices, current.choices.length);
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
                ancestors.addAll(blocked);
                List<CellPos> joined = blocked.isEmpty() ? follow(current.arrival, new HashSet<>(ancestors)) : null;
                if (joined != null && joined.size() > 1) {
                    List<CellPos> cells = path(current.arrival, joined.subList(1, joined.size()));
                    publish(cells);
                    return finish(cells, stepsBetween(cells));
                }
                List<Step> candidates = steps(current.arrival.cell);
                if (current.parent != null) {
                    LakePlan lake = data.lakes().get(new LakePlan.Inlet(current.parent.cell, current.arrival.cell));
                    if (lake != null) {
                        pending.add(new Node(current.arrival, extend(current.choices, candidates.size()), order++, current.parent, null, lake));
                    }
                }
                for (int index = 0; index < candidates.size(); index++) {
                    Step step = candidates.get(index);
                    Arrival next = advance(current.arrival, step);
                    if ((step.wall() && !current.arrival.wall) || next.awaySteps > awayStepAllowance || isDivide(step.pos()) || ancestors.contains(step.pos())
                        || (!blocked.isEmpty() && current.arrival.equals(start) && data.cell(step.pos()).height() >= data.cell(source).height())) {
                        continue;
                    }
                    if (expanded.contains(next)) {
                        continue;
                    }
                    pending.add(new Node(next, extend(current.choices, index), order++, current.arrival, step));
                }
            }
            return null;
        }

        private boolean canSwitchTo(Node next) {
            if (tip == null || tip.arrival.equals(next.parent) || (next.lake != null && tip.arrival.equals(next.arrival))) {
                return true;
            }
            int fork = Arrays.mismatch(tip.choices, next.choices);
            return fork < 0 || canReturnToDepth(fork);
        }

        private boolean canReturnToDepth(int fork) {
            Arrival cursor = tip.arrival;
            for (int i = tip.choices.length; i >= fork; i--) {
                if (data.cell(cursor.cell).height() > returnCeiling) {
                    return false;
                }
                cursor = parents.get(cursor);
            }
            return true;
        }

        private boolean withinWindow(int[] choices) {
            return choices.length >= committedDepth && Arrays.equals(choices, 0, committedDepth, committed, 0, committedDepth);
        }

        private void advanceWindow(int[] choices, int depth) {
            furthest = Math.max(furthest, depth);
            int floor = depth - maxBacktrack;
            if (floor > committedDepth) {
                committed = choices;
                committedDepth = floor;
            }
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
            if (!blocked.isEmpty()) {
                return;
            }
            List<Step> chosen = stepsBetween(cells);
            Arrival arrival = new Arrival(cells.get(0), 0, false);
            for (Step step : chosen) {
                outlets.putIfAbsent(arrival, step);
                arrival = advance(arrival, step);
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
                Arrival next = advance(cursor, step);
                if ((step.wall() && !cursor.wall) || next.awaySteps > awayStepAllowance || isDivide(step.pos())) {
                    return null;
                }
                cursor = next;
            }
            return null;
        }
    }

    private record Passage(List<CellPos> channel, List<Flowline.Lake> lakes, Set<CellPos> blocked) {
        List<CellPos> through(List<CellPos> tail) {
            List<CellPos> cells = new ArrayList<>(channel);
            cells.addAll(tail);
            return cells;
        }

        List<Flowline.Lake> flooded(Feature feature, Map<CellPos, Integer> depths) {
            List<Flowline.Lake> result = new ArrayList<>(lakes);
            result.add(new Flowline.Lake(feature, depths));
            return result;
        }
    }

    private record OutletTask(LakeFill.Spill spill, Passage passage, Flowline basin) {}

    private record Segment(Notes notes, Passage passage, OutletTask inlet, ArrayDeque<OutletTask> outlets) {
        Segment(Notes notes, Passage passage, OutletTask inlet) {
            this(notes, passage, inlet, new ArrayDeque<>());
        }
    }

    private final class LakeRoutes {
        private final Set<SpillSearch.Outlet> examined = new HashSet<>();
        private final ArrayDeque<Segment> stack = new ArrayDeque<>();
        private Flowline fallback;

        Flowline find(CellPos source) {
            stack.push(new Segment(new Notes(source), new Passage(List.of(), List.of(), Set.of()), null));
            while (!stack.isEmpty()) {
                Segment segment = stack.element();
                if (!segment.outlets.isEmpty()) {
                    OutletTask task = segment.outlets.remove();
                    stack.push(new Segment(new Notes(task.spill.lip(), task.passage.blocked()), task.passage, task));
                    continue;
                }
                Flowline route = segment.notes.advanceToLakeOrOutlet();
                if (route != null) {
                    return new Flowline(segment.passage.through(route.cells()), route.terminus(), true, segment.passage.lakes());
                }
                if (segment.notes.lake != null) {
                    prepareOutlets(segment);
                    continue;
                }
                stack.pop();
                if (!stack.isEmpty() && !mayRetryAfter(segment)) {
                    return segment.inlet.basin;
                }
            }
            return fallback == null ? new Flowline(List.of(source), null, false) : fallback;
        }

        private boolean mayRetryAfter(Segment segment) {
            Notes tail = segment.notes;
            List<CellPos> crossing = segment.inlet.spill.route();
            int distance = tail.furthest + crossing.size() - 1;
            if (distance > maxBacktrack || !tail.canReturnToDepth(0)
                || crossing.stream().anyMatch(pos -> data.cell(pos).height() > tail.returnCeiling)) {
                return false;
            }
            Notes parent = stack.element().notes;
            Node candidate = parent.lake;
            int entryDepth = candidate.choices.length - 1;
            parent.advanceWindow(candidate.choices, entryDepth + distance);
            parent.tip = new Node(candidate.arrival, Arrays.copyOf(candidate.choices, entryDepth), 0, candidate.parent, null);
            parent.returnCeiling = tail.returnCeiling;
            return true;
        }

        private void prepareOutlets(Segment segment) {
            Notes notes = segment.notes;
            Node candidate = notes.lake;
            Passage prefix = segment.passage;
            LakePlan plan = candidate.lake;
            if (!Collections.disjoint(prefix.blocked(), plan.closed().keySet())) {
                return;
            }
            List<CellPos> incoming = notes.path(candidate.arrival, List.of());
            CellPos shore = incoming.stream().filter(plan.closed()::containsKey).findFirst().orElseThrow();
            Flowline basin = new Flowline(prefix.through(incoming.subList(0, incoming.indexOf(shore) + 1)), shore, true,
                prefix.flooded(Feature.ENDORHEIC, plan.closed()));
            if (fallback == null || basin.lakes().size() > fallback.lakes().size()) {
                fallback = basin;
            }
            ArrayDeque<OutletTask> inland = new ArrayDeque<>();
            for (LakeFill.Spill spill : plan.spills()) {
                enqueue(prefix, incoming, spill, basin, segment.outlets, inland);
            }
            segment.outlets.addAll(inland);
        }

        private void enqueue(Passage prefix, List<CellPos> incoming, LakeFill.Spill spill, Flowline basin,
                             ArrayDeque<OutletTask> pending, ArrayDeque<OutletTask> inland) {
            if (prefix.blocked().contains(spill.lip()) || !Collections.disjoint(prefix.blocked(), spill.overFill().keySet())) {
                return;
            }
            Set<CellPos> exclude = new HashSet<>(prefix.blocked());
            exclude.addAll(incoming);
            exclude.addAll(spill.overFill().keySet());
            exclude.remove(spill.lip());
            SpillSearch.Outlet outlet = spillSearch.outlet(spill.lip(), exclude);
            if (!examined.add(outlet)) {
                return;
            }
            List<CellPos> channel = new ArrayList<>(incoming);
            for (CellPos pos : spill.route().subList(1, spill.route().size())) {
                int repeated = channel.indexOf(pos);
                if (repeated >= 0) {
                    channel.subList(repeated + 1, channel.size()).clear();
                } else {
                    channel.add(pos);
                }
            }
            channel.remove(channel.size() - 1);
            Passage passage = new Passage(prefix.through(channel), prefix.flooded(Feature.KETTLE, spill.overFill()), exclude);
            OutletTask task = new OutletTask(spill, passage, basin);
            if (outlet.possibleShore()) {
                pending.add(task);
            } else {
                inland.add(task);
            }
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

    private record Node(Arrival arrival, int[] choices, int order, Arrival parent, Step step, LakePlan lake) {
        Node(Arrival arrival, int[] choices, int order, Arrival parent, Step step) {
            this(arrival, choices, order, parent, step, null);
        }
    }
}
