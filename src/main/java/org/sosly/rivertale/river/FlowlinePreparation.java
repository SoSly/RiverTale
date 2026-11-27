package org.sosly.rivertale.river;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;

public final class FlowlinePreparation {
    private final FlowlineTracer tracer;
    private final UpstreamSearch search;
    private final Map<RegionPos, List<Flowline>> traced = new HashMap<>();
    private final Map<RegionPos, Set<Flowline>> crossing = new HashMap<>();
    private final Set<RegionPos> upstream = new HashSet<>();
    private final Queue<RegionPos> pending = new ArrayDeque<>();

    private FlowlinePreparation(FlowlineTracer tracer) {
        this.tracer = tracer;
        this.search = new UpstreamSearch(tracer);
    }

    public static Result prepare(Region region) {
        return prepare(region, new FlowlineTracer(RiverPreparation.prepare(Set.of(region.pos()))));
    }

    static Result prepare(Region region, FlowlineTracer tracer) {
        return prepare(Set.of(region.pos()), tracer, () -> { });
    }

    static Result prepare(Set<RegionPos> roots, FlowlineTracer tracer, Runnable checkCancelled) {
        Timer.Record timer = Store.getTimer(FlowlinePreparation.class, "prepare").start();
        try {
            FlowlinePreparation preparation = new FlowlinePreparation(tracer);
            // Traces can reuse downstream paths. Establish those paths in geographic order,
            // not in the order a chunk happens to discover its upstream regions.
            for (RegionPos source : tracer.preparedSourceRegions().stream().sorted(FlowlinePreparation::compare).toList()) {
                checkCancelled.run();
                preparation.paths(source);
            }
            roots.stream().sorted(FlowlinePreparation::compare).forEach(preparation::include);
            preparation.discover(checkCancelled);
            Store.getDistribution(FlowlinePreparation.class, "upstreamRegions").record(preparation.upstream.size());
            Store.getDistribution(FlowlinePreparation.class, "examinedRegions").record(preparation.search.regionCount());
            Store.getDistribution(FlowlinePreparation.class, "upstreamCells").record(preparation.search.cellCount());
            Store.getDistribution(FlowlinePreparation.class, "tracedRegions").record(preparation.traced.size());
            Map<RegionPos, List<Flowline>> contributors = new LinkedHashMap<>();
            preparation.upstream.stream().sorted(FlowlinePreparation::compare).forEach(pos -> contributors.put(pos, preparation.paths(pos)));
            return new Result(contributors, preparation.traced, tracer);
        } finally {
            timer.stop();
        }
    }

    FlowlinePreparation(Result prepared) {
        this(prepared.tracer());
        traced.putAll(prepared.traced());
        traced.values().forEach(paths -> paths.forEach(this::index));
    }

    private void discover(Runnable checkCancelled) {
        while (!pending.isEmpty()) {
            checkCancelled.run();
            RegionPos target = pending.remove();
            Region region = tracer.region(target);
            for (RegionPos source : search.expand(region.cells(), checkCancelled).stream().sorted(FlowlinePreparation::compare).toList()) {
                checkCancelled.run();
                paths(source);
            }
            paths(target);
            for (Flowline path : crossing.getOrDefault(target, Set.of())) {
                includeUpstream(path, target);
            }
        }
    }

    private void index(Flowline path) {
        Set<RegionPos> traversed = new HashSet<>();
        path.cells().forEach(cell -> traversed.add(cell.getRegion()));
        path.lakes().forEach(lake -> lake.depths().keySet().forEach(cell -> traversed.add(cell.getRegion())));
        for (RegionPos region : traversed) {
            crossing.computeIfAbsent(region, key -> new HashSet<>()).add(path);
            if (upstream.contains(region)) {
                includeUpstream(path, region);
            }
        }
    }

    private void includeUpstream(Flowline path, RegionPos target) {
        int lastEntry = -1;
        for (int i = path.cells().size() - 1; i >= 0; i--) {
            if (target.contains(path.cells().get(i))) {
                lastEntry = i;
                break;
            }
        }
        if (lastEntry < 0 && !path.cells().isEmpty()) {
            include(path.cells().get(0).getRegion());
        }
        for (int i = 0; i <= lastEntry; i++) {
            include(path.cells().get(i).getRegion());
        }
    }

    private void include(RegionPos pos) {
        if (!pos.containsOutOfBoundsCells() && upstream.add(pos)) {
            pending.add(pos);
        }
    }

    List<Flowline> paths(RegionPos pos) {
        return traced.computeIfAbsent(pos, key -> {
            List<Flowline> paths = SourceTrace.trace(tracer.region(key), tracer);
            paths.forEach(this::index);
            return paths;
        });
    }

    private static int compare(RegionPos first, RegionPos second) {
        int x = Integer.compare(first.x(), second.x());
        return x == 0 ? Integer.compare(first.z(), second.z()) : x;
    }

    public record Result(Map<RegionPos, List<Flowline>> upstream, Map<RegionPos, List<Flowline>> traced, FlowlineTracer tracer) {
        public List<Flowline> trace(Region region) {
            return SourceTrace.trace(region, tracer);
        }

        public Result {
            upstream = Collections.unmodifiableMap(new LinkedHashMap<>(upstream));
            traced = Map.copyOf(traced);
        }
    }
}
