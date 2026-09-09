package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;

public class FlowlineTracer {
    private final Set<RegionPos> allowedRegions = new HashSet<>();
    private final List<CellPos> termini;
    private final Set<CellPos> terminusCells = new HashSet<>();
    private final Function<CellPos, Cell> cells;
    private final long mergeDistanceSquared;

    public FlowlineTracer(Collection<Region> regions) {
        this(regions, CellCache.get()::getOrComputeWithFlow, CommonConfig.get().mergeThreshold());
    }

    FlowlineTracer(Collection<Region> regions, Function<CellPos, Cell> cells, int mergeThreshold) {
        this.cells = cells;
        this.mergeDistanceSquared = (long) mergeThreshold * mergeThreshold;
        for (Region region : regions) {
            allowedRegions.add(region.pos());
        }
        for (Region region : regions) {
            for (Boundary boundary : region.boundaries()) {
                if (allowedRegions.contains(boundary.land().getRegion())) {
                    terminusCells.add(boundary.land());
                }
            }
        }
        termini = new ArrayList<>(terminusCells);
        termini.sort(Comparator.comparingInt(CellPos::x).thenComparingInt(CellPos::z));
    }

    public Flowline trace(CellPos source) {
        Timer.Record timer = Store.getTimer(FlowlineTracer.class, "trace").start();
        try {
            return traceSource(source);
        } finally {
            timer.stop();
        }
    }

    private Flowline traceSource(CellPos source) {
        List<CellPos> path = new ArrayList<>();
        path.add(source);
        if (!allowedRegions.contains(source.getRegion())) {
            return new Flowline(path, null, false);
        }

        Set<CellPos> visited = new HashSet<>();
        visited.add(source);
        CellPos current = source;
        while (!terminusCells.contains(current)) {
            CellPos target = nearestTerminus(current);
            if (target == null) {
                return new Flowline(path, null, false);
            }
            CellPos next = selectNext(current, target);
            if (next == null || visited.contains(next)) {
                return new Flowline(path, null, false);
            }
            if (cells.apply(next).feature() == Feature.DIVIDE) {
                return new Flowline(path, null, false);
            }
            visited.add(next);
            path.add(next);
            current = next;
        }
        return new Flowline(path, current, true);
    }

    private CellPos nearestTerminus(CellPos from) {
        CellPos nearest = null;
        long bestDistance = Long.MAX_VALUE;
        for (CellPos terminus : termini) {
            long distance = distanceSquared(from, terminus);
            if (distance < bestDistance) {
                nearest = terminus;
                bestDistance = distance;
            }
        }
        return nearest;
    }

    private CellPos selectNext(CellPos current, CellPos target) {
        long distance = distanceSquared(current, target);
        if (distance <= mergeDistanceSquared) {
            return forceTowardTerminus(current, target, distance);
        }

        Cell cell = cells.apply(current);
        for (FlowDirection direction : cell.flowDirections()) {
            CellPos next = current.relative(direction);
            if (makesProgress(next, target, distance)) {
                return next;
            }
        }

        FlowDirection toward = FlowDirection.toward(current, target);
        for (FlowDirection direction : cell.flowDirections()) {
            CellPos next = current.relative(direction.rotateToward(toward));
            if (makesProgress(next, target, distance)) {
                return next;
            }
        }
        return null;
    }

    private CellPos forceTowardTerminus(CellPos current, CellPos target, long distance) {
        CellPos nearest = null;
        long bestDistance = distance;
        for (FlowDirection direction : FlowDirection.D8) {
            CellPos next = current.relative(direction);
            if (!makesProgress(next, target, bestDistance)) {
                continue;
            }
            nearest = next;
            bestDistance = distanceSquared(next, target);
        }
        return nearest;
    }

    private boolean makesProgress(CellPos next, CellPos target, long distance) {
        return allowedRegions.contains(next.getRegion()) && distanceSquared(next, target) < distance;
    }

    private static long distanceSquared(CellPos from, CellPos to) {
        long dx = (long) to.x() - from.x();
        long dz = (long) to.z() - from.z();
        return dx * dx + dz * dz;
    }
}
