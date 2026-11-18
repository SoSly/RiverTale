package org.sosly.rivertale.river;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import org.sosly.rivertale.cell.Cell;
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

    public FlowlineTracer(PreparedRiverData data) {
        this.data = data;
        this.awayStepAllowance = data.awayStepAllowance();
    }

    FlowlineTracer(Collection<Region> regions, Function<CellPos, Cell> cells, int fallback) {
        this(regions, cells, fallback, CommonConfig.get().awayStepAllowance());
    }

    FlowlineTracer(Collection<Region> regions, Function<CellPos, Cell> cells, int fallback, int away) {
        this(RiverPreparation.forRegions(regions, pos -> regions, cells, fallback, away));
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
        Search search = new Search(source);
        try {
            Flowline path = search.run();
            if (search.retried) {
                Ratio successes = Store.getRatio(FlowlineTracer.class, "retrySuccess");
                if (path.isValid()) {
                    successes.success();
                } else {
                    successes.failure();
                }
            }
            return path;
        } finally {
            Store.getDistribution(FlowlineTracer.class, "awayLimitRejections").record(search.awayLimitRejections);
            Store.getDistribution(FlowlineTracer.class, "loopRejections").record(search.loopRejections);
            Store.getDistribution(FlowlineTracer.class, "contextualFailures").record(search.contextualFailures);
            if (search.retried) {
                Store.getTimer(FlowlineTracer.class, "backtracking").record(System.nanoTime() - search.failureStarted);
            }
        }
    }

    private final class Search {
        private final List<Frame> stack = new ArrayList<>();
        private final Map<CellPos, Integer> exhausted = new HashMap<>();
        private final Set<CellPos> active = new HashSet<>();
        private int awayLimitRejections;
        private int loopRejections;
        private int contextualFailures;
        private final Ratio deadEndHits = Store.getRatio(FlowlineTracer.class, "deadEndHits");
        private List<CellPos> firstFailure;
        private long failureStarted;
        private boolean retried;

        private Search(CellPos source) {
            stack.add(frame(source, 0));
            active.add(source);
        }

        private Flowline run() {
            while (!stack.isEmpty()) {
                Frame current = stack.get(stack.size() - 1);
                if (isTerminus(current.pos)) {
                    recordNudgingRise();
                    recordAwaySteps();
                    return new Flowline(positions(), current.pos, true);
                }
                if (current.nextIndex == current.candidates.size()) {
                    rememberFailure();
                    if (current.pathDependent && proveClosedBranch(current)) {
                        current.pathDependent = false;
                    }
                    if (!current.pathDependent) {
                        exhausted.merge(current.pos, current.awaySteps, Math::min);
                    } else {
                        contextualFailures++;
                    }
                    active.remove(current.pos);
                    stack.remove(stack.size() - 1);
                    if (current.pathDependent && !stack.isEmpty()) {
                        stack.get(stack.size() - 1).pathDependent = true;
                    }
                    continue;
                }
                if (current.nextIndex > 0) {
                    retried = true;
                }
                Step candidate = current.candidates.get(current.nextIndex++);
                int awaySteps = candidate.approaching() ? 0 : current.awaySteps + 1;
                if (awaySteps > awayStepAllowance) {
                    rememberFailure();
                    awayLimitRejections++;
                    continue;
                }
                CellPos next = candidate.pos();
                if (active.contains(next)) {
                    rememberFailure();
                    loopRejections++;
                    current.pathDependent = true;
                    continue;
                }
                if (isExhausted(next, awaySteps)) {
                    continue;
                }
                if (isDivide(next)) {
                    rememberFailure();
                    exhausted.put(next, 0);
                    continue;
                }
                stack.add(frame(next, awaySteps));
                active.add(next);
            }
            return new Flowline(firstFailure, null, false);
        }

        private boolean proveClosedBranch(Frame start) {
            Timer.Record timer = Store.getTimer(FlowlineTracer.class, "closedBranchSearch").start();
            Ratio checks = Store.getRatio(FlowlineTracer.class, "closedBranchChecks");
            try {
                // Ignore active cells here: only independently unreachable branches may be cached.
                Map<CellPos, Integer> visited = new HashMap<>();
                ArrayDeque<SearchState> pending = new ArrayDeque<>();
                pending.add(new SearchState(start.pos, start.awaySteps));
                visited.put(start.pos, start.awaySteps);
                while (!pending.isEmpty()) {
                    SearchState current = pending.remove();
                    if (visited.get(current.pos()) < current.awaySteps()) {
                        continue;
                    }
                    if (isTerminus(current.pos())) {
                        checks.failure();
                        return false;
                    }
                    for (Step step : steps(current.pos())) {
                        int awaySteps = step.approaching() ? 0 : current.awaySteps() + 1;
                        if (awaySteps > awayStepAllowance || visited.getOrDefault(step.pos(), Integer.MAX_VALUE) <= awaySteps
                            || exhausted.getOrDefault(step.pos(), Integer.MAX_VALUE) <= awaySteps || isDivide(step.pos())) {
                            continue;
                        }
                        visited.put(step.pos(), awaySteps);
                        pending.add(new SearchState(step.pos(), awaySteps));
                    }
                }
                visited.forEach((pos, awaySteps) -> exhausted.merge(pos, awaySteps, Math::min));
                checks.success();
                return true;
            } finally {
                timer.stop();
            }
        }

        private void recordNudgingRise() {
            if (!CommonConfig.get().enableMetrics()) {
                return;
            }
            long total = 0;
            Distribution steps = Store.getDistribution(FlowlineTracer.class, "uphillNudgeBlocks");
            for (int i = 0; i < stack.size() - 1; i++) {
                Frame frame = stack.get(i);
                long rise = frame.candidates.get(frame.nextIndex - 1).nudgingRise();
                if (rise > 0) {
                    total += rise;
                    steps.record(rise);
                }
            }
            Store.getDistribution(FlowlineTracer.class, "nudgingRiseBlocks").record(total);
        }

        private void recordAwaySteps() {
            if (!CommonConfig.get().enableMetrics()) {
                return;
            }
            int total = 0;
            int longestRun = 0;
            for (Frame frame : stack) {
                if (frame.awaySteps > 0) {
                    total++;
                    longestRun = Math.max(longestRun, frame.awaySteps);
                }
            }
            Store.getDistribution(FlowlineTracer.class, "awaySteps").record(total);
            Store.getDistribution(FlowlineTracer.class, "maxConsecutiveAwaySteps").record(longestRun);
        }

        private Frame frame(CellPos pos, int awaySteps) {
            return new Frame(pos, steps(pos), awaySteps);
        }

        private boolean isExhausted(CellPos pos, int awaySteps) {
            if (exhausted.getOrDefault(pos, Integer.MAX_VALUE) <= awaySteps) {
                deadEndHits.success();
                return true;
            }
            deadEndHits.failure();
            return false;
        }

        private void rememberFailure() {
            if (firstFailure != null) {
                return;
            }
            firstFailure = positions();
            failureStarted = System.nanoTime();
        }

        private List<CellPos> positions() {
            return stack.stream().map(frame -> frame.pos).toList();
        }
    }

    private record SearchState(CellPos pos, int awaySteps) {
    }

    private static final class Frame {
        private final CellPos pos;
        private final List<Step> candidates;
        private final int awaySteps;
        private int nextIndex;
        private boolean pathDependent;

        private Frame(CellPos pos, List<Step> candidates, int awaySteps) {
            this.pos = pos;
            this.candidates = candidates;
            this.awaySteps = awaySteps;
        }
    }
}
