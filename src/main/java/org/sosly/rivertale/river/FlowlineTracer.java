package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.concurrent.ConcurrentHashMap;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Distribution;
import org.sosly.rivertale.metric.Ratio;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.world.RiverShaping;

public class FlowlineTracer {
    private static final int FALLBACK_APPROACH_CELLS = 5;

    private final Routing routing;
    private final Map<CellPos, List<Step>> moves = new ConcurrentHashMap<>();
    private final Map<CellPos, Feature> features = new ConcurrentHashMap<>();
    private final Function<CellPos, Cell> cells;
    private final long fallbackDistanceSquared;
    private final boolean enableNudging;
    private final int nudgingLimit;

    public FlowlineTracer(Collection<Region> regions) {
        this(regions, RiverShaping::classify, FALLBACK_APPROACH_CELLS);
    }

    FlowlineTracer(Collection<Region> regions, Function<CellPos, Cell> cells, int fallbackThreshold) {
        this.cells = cells;
        this.enableNudging = CommonConfig.get().enableNudging();
        this.nudgingLimit = CommonConfig.get().nudgingLimit();
        this.fallbackDistanceSquared = (long) fallbackThreshold * fallbackThreshold;
        Set<RegionPos> allowed = new HashSet<>();
        Set<CellPos> termini = new HashSet<>();
        regions.forEach(region -> allowed.add(region.pos()));
        for (Region region : regions) {
            for (Boundary boundary : region.boundaries()) {
                if (allowed.contains(boundary.land().getRegion())) {
                    termini.add(boundary.land());
                }
            }
        }
        List<CellPos> ordered = termini.stream().sorted(Comparator.comparingInt(CellPos::x)
            .thenComparingInt(CellPos::z)).toList();
        this.routing = new Routing(Set.copyOf(allowed), ordered, Set.copyOf(termini));
    }

    private Routing routing(RegionPos pos) {
        return routing;
    }

    private boolean isDivide(CellPos pos) {
        return features.computeIfAbsent(pos, key -> cells.apply(key).feature()) == Feature.DIVIDE;
    }

    private boolean isTerminus(CellPos pos) {
        return routing(pos.getRegion()).terminusCells().contains(pos);
    }

    private List<Step> steps(CellPos pos) {
        return moves.computeIfAbsent(pos, key -> {
            Routing local = routing(key.getRegion());
            if (!local.allowedRegions().contains(key.getRegion()) || local.termini().isEmpty() || isTerminus(key)) {
                return List.of();
            }
            return candidates(key, nearestTerminus(key), local);
        });
    }

    private record Routing(Set<RegionPos> allowedRegions, List<CellPos> termini, Set<CellPos> terminusCells) {
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
        int[] heights = path.cells().stream().mapToInt(pos -> cells.apply(pos).averageEstimatedTerrainHeight()).toArray();
        int outlet = RiverShaping.getSeaLevel();
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
        if (!local.allowedRegions().contains(source.getRegion()) || local.termini().isEmpty()) {
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
            if (search.retried) {
                Store.getTimer(FlowlineTracer.class, "backtracking").record(System.nanoTime() - search.failureStarted);
            }
        }
    }

    private final class Search {
        private final List<Frame> stack = new ArrayList<>();
        private final Set<CellPos> exhausted = new HashSet<>();
        private final Ratio deadEndHits = Store.getRatio(FlowlineTracer.class, "deadEndHits");
        private List<CellPos> firstFailure;
        private long failureStarted;
        private boolean retried;

        private Search(CellPos source) {
            stack.add(frame(source));
        }

        private Flowline run() {
            while (!stack.isEmpty()) {
                Frame current = stack.get(stack.size() - 1);
                if (isTerminus(current.pos)) {
                    recordNudgingRise();
                    return new Flowline(positions(), current.pos, true);
                }
                if (current.nextIndex == current.candidates.size()) {
                    rememberFailure();
                    exhausted.add(current.pos);
                    stack.remove(stack.size() - 1);
                    continue;
                }
                if (current.nextIndex > 0) {
                    retried = true;
                }
                CellPos next = current.candidates.get(current.nextIndex++).pos();
                if (isExhausted(next)) {
                    continue;
                }
                if (isDivide(next)) {
                    rememberFailure();
                    exhausted.add(next);
                    continue;
                }
                stack.add(frame(next));
            }
            return new Flowline(firstFailure, null, false);
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

        private Frame frame(CellPos pos) {
            return new Frame(pos, steps(pos));
        }

        private boolean isExhausted(CellPos pos) {
            if (exhausted.contains(pos)) {
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

    private record Step(CellPos pos, long nudgingRise) {
    }

    private static final class Frame {
        private final CellPos pos;
        private final List<Step> candidates;
        private int nextIndex;

        private Frame(CellPos pos, List<Step> candidates) {
            this.pos = pos;
            this.candidates = candidates;
        }
    }
    private CellPos nearestTerminus(CellPos from) {
        CellPos nearest = null;
        long bestDistance = Long.MAX_VALUE;
        for (CellPos terminus : routing(from.getRegion()).termini()) {
            long distance = distanceSquared(from, terminus);
            if (distance < bestDistance) {
                nearest = terminus;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    private List<Step> candidates(CellPos current, CellPos target, Routing local) {
        long distance = distanceSquared(current, target);

        Map<CellPos, Step> candidates = new LinkedHashMap<>();
        Cell cell = cells.apply(current);
        for (FlowDirection direction : cell.flowDirections()) {
            CellPos next = current.relative(direction);
            if (makesProgress(next, target, distance, local)) {
                candidates.put(next, new Step(next, 0));
            }
        }

        if (enableNudging) {
            int currentHeight = cell.averageEstimatedTerrainHeight();
            FlowDirection toward = FlowDirection.toward(current, target);
            for (FlowDirection direction : cell.flowDirections()) {
                CellPos next = current.relative(direction.rotateToward(toward));
                if (candidates.containsKey(next) || !makesProgress(next, target, distance, local)) {
                    continue;
                }
                long rise = (long) cells.apply(next).averageEstimatedTerrainHeight() - currentHeight;
                if (rise <= nudgingLimit) {
                    candidates.put(next, new Step(next, Math.max(0, rise)));
                }
            }
        }
        if (distance <= fallbackDistanceSquared) {
            CellPos direct = forceTowardTerminus(current, target, distance, local);
            if (direct != null) {
                candidates.putIfAbsent(direct, new Step(direct, 0));
            }
        }
        return List.copyOf(candidates.values());
    }

    private CellPos forceTowardTerminus(CellPos current, CellPos target, long distance, Routing local) {
        CellPos nearest = null;
        long bestDistance = distance;
        for (FlowDirection direction : FlowDirection.D8) {
            CellPos next = current.relative(direction);
            if (!makesProgress(next, target, bestDistance, local)) {
                continue;
            }
            nearest = next;
            bestDistance = distanceSquared(next, target);
        }
        return nearest;
    }

    private boolean makesProgress(CellPos next, CellPos target, long distance, Routing local) {
        return local.allowedRegions().contains(next.getRegion()) && distanceSquared(next, target) < distance;
    }

    private static long distanceSquared(CellPos from, CellPos to) {
        long dx = (long) to.x() - from.x();
        long dz = (long) to.z() - from.z();
        return dx * dx + dz * dz;
    }
}
