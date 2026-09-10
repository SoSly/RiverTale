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
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionExplorer;

public final class FlowlinePreparation {
    private final Map<RegionPos, Set<Region>> routing = new HashMap<>();
    private final Map<RegionPos, List<Flowline>> traced = new HashMap<>();
    private final Set<RegionPos> upstream = new HashSet<>();
    private final Queue<RegionPos> pending = new ArrayDeque<>();

    private FlowlinePreparation() {
    }

    public static Result prepare(Region region) {
        Timer.Record timer = Store.getTimer(FlowlinePreparation.class, "prepare").start();
        try {
            FlowlinePreparation preparation = new FlowlinePreparation();
            preparation.include(region.pos());
            preparation.discover();
            Store.getDistribution(FlowlinePreparation.class, "upstreamRegions").record(preparation.upstream.size());
            Store.getDistribution(FlowlinePreparation.class, "examinedRegions").record(preparation.routing.size());
            Store.getDistribution(FlowlinePreparation.class, "tracedRegions").record(preparation.traced.size());
            Map<RegionPos, List<Flowline>> contributors = new LinkedHashMap<>();
            preparation.upstream.stream().sorted(FlowlinePreparation::compare).forEach(pos -> contributors.put(pos, preparation.paths(pos)));
            return new Result(contributors, preparation.traced);
        } finally {
            timer.stop();
        }
    }

    private void discover() {
        while (!pending.isEmpty()) {
            RegionPos target = pending.remove();
            paths(target);
            for (RegionPos source : RegionExplorer.potentialSources(target)) {
                if (workingSet(source).stream().noneMatch(region -> region.pos().equals(target))) {
                    continue;
                }
                for (Flowline path : paths(source)) {
                    includeUpstream(path, target);
                }
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
        for (int i = 0; i <= lastEntry; i++) {
            include(path.cells().get(i).getRegion());
        }
    }

    private void include(RegionPos pos) {
        if (!pos.containsOutOfBoundsCells() && upstream.add(pos)) {
            pending.add(pos);
        }
    }

    private Set<Region> workingSet(RegionPos pos) {
        return routing.computeIfAbsent(pos, key -> RegionExplorer.discover(key.getWorldPosition(0)));
    }

    private List<Flowline> paths(RegionPos pos) {
        return traced.computeIfAbsent(pos, key -> FlowlinePreview.trace(RegionCache.get().getOrCompute(key), workingSet(key)));
    }

    private static int compare(RegionPos first, RegionPos second) {
        int x = Integer.compare(first.x(), second.x());
        return x == 0 ? Integer.compare(first.z(), second.z()) : x;
    }

    public record Result(Map<RegionPos, List<Flowline>> upstream, Map<RegionPos, List<Flowline>> traced) {
        public Result {
            upstream = Collections.unmodifiableMap(new LinkedHashMap<>(upstream));
            traced = Map.copyOf(traced);
        }
    }
}
