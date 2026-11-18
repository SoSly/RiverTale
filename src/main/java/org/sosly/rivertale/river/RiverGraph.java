package org.sosly.rivertale.river;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.river.PreparedRiverData.CellData;
import org.sosly.rivertale.river.PreparedRiverData.Routing;
import org.sosly.rivertale.river.PreparedRiverData.Step;
import org.sosly.rivertale.world.RiverShaping;

final class RiverGraph {
    private final Function<RegionPos, Collection<Region>> regionProvider;
    private final Function<RegionPos, Region> regionLoader;
    private final Function<CellPos, Cell> cellLoader;
    private final Map<RegionPos, Region> preparedRegions = new HashMap<>();
    private final Map<CellPos, CellData> preparedCells = new HashMap<>();
    private final Map<RegionPos, Routing> routing = new HashMap<>();
    private final Map<CellPos, List<Step>> moves = new HashMap<>();
    private final Map<CellPos, List<CellPos>> incoming = new HashMap<>();
    private final Map<RegionPos, List<CellPos>> sources = new HashMap<>();
    private final long fallbackDistanceSquared;
    private final int awayStepAllowance;
    private final boolean enableNudging;
    private final int nudgingLimit;

    RiverGraph(Function<RegionPos, Region> regionLoader, Function<RegionPos, Collection<Region>> regionProvider,
               Function<CellPos, Cell> cellLoader, int fallback, int awayStepAllowance) {
        this.regionLoader = regionLoader;
        this.regionProvider = regionProvider;
        this.cellLoader = cellLoader;
        this.fallbackDistanceSquared = (long) fallback * fallback;
        this.awayStepAllowance = awayStepAllowance;
        this.enableNudging = CommonConfig.get().enableNudging();
        this.nudgingLimit = CommonConfig.get().nudgingLimit();
    }

    Region region(RegionPos pos) {
        return preparedRegions.computeIfAbsent(pos, regionLoader);
    }

    CellData cell(CellPos pos) {
        return preparedCells.computeIfAbsent(pos, key -> {
            Cell value = cellLoader.apply(key);
            return new CellData(value.feature(), value.flowDirections(), value.averageEstimatedTerrainHeight());
        });
    }

    List<CellPos> sources(RegionPos pos) {
        return sources.computeIfAbsent(pos, key -> {
            Region region = region(key);
            if (!hasDestinations(key)) {
                return List.of();
            }
            List<CellPos> found = new ArrayList<>();
            for (CellPos cell : region.cells()) {
                if (cell(cell).feature().type == CellType.SOURCE) {
                    found.add(cell);
                }
            }
            return List.copyOf(found);
        });
    }

    PreparedRiverData finish() {
        Store.getDistribution(RiverPreparation.class, "regions").record(preparedRegions.size());
        Store.getDistribution(RiverPreparation.class, "cells").record(preparedCells.size());
        Store.getDistribution(RiverPreparation.class, "candidateCells").record(moves.size());
        return new PreparedRiverData(preparedRegions, preparedCells, routing, moves, incoming, sources,
            awayStepAllowance, RiverShaping.getSeaLevel());
    }

    Routing routing(RegionPos pos) {
        return routing.computeIfAbsent(pos, key -> {
            region(key);
            Timer.Record timer = Store.getTimer(RiverGraph.class, "routing").start();
            try {
                Collection<Region> regions = key.containsOutOfBoundsCells() ? List.of() : regionProvider.apply(key);
                regions.forEach(region -> preparedRegions.put(region.pos(), region));
                Map<RegionPos, Region> byPosition = new HashMap<>();
                regions.forEach(region -> byPosition.put(region.pos(), region));
                Set<RegionPos> destinations = downstreamRegions(key, byPosition);
                Set<RegionPos> allowed = new HashSet<>();
                for (RegionPos destination : destinations) {
                    if (canEnterRegion(byPosition.get(key), byPosition.get(destination))) {
                        allowed.add(destination);
                    }
                }
                Set<CellPos> termini = new HashSet<>();
                Set<CellPos> waterCells = new HashSet<>();
                for (Region region : regions) {
                    for (Boundary boundary : region.boundaries()) {
                        waterCells.add(boundary.water());
                        if (destinations.contains(region.pos()) && destinations.contains(boundary.land().getRegion())) {
                            termini.add(boundary.land());
                        }
                    }
                }
                List<CellPos> ordered = termini.stream().sorted(Comparator.comparingInt(CellPos::x)
                    .thenComparingInt(CellPos::z)).toList();
                return new Routing(Set.copyOf(allowed), ordered, Set.copyOf(termini), Set.copyOf(waterCells));
            } finally {
                timer.stop();
            }
        });
    }

    private Set<RegionPos> downstreamRegions(RegionPos origin, Map<RegionPos, Region> regions) {
        if (!regions.containsKey(origin)) {
            return Set.of();
        }
        Set<RegionPos> found = new HashSet<>();
        ArrayDeque<RegionPos> pending = new ArrayDeque<>();
        found.add(origin);
        pending.add(origin);
        while (!pending.isEmpty()) {
            Region current = regions.get(pending.remove());
            for (FlowDirection direction : FlowDirection.D4) {
                Region neighbor = regions.get(current.pos().relative(direction));
                if (neighbor != null && canEnterRegion(current, neighbor) && found.add(neighbor.pos())) {
                    pending.add(neighbor.pos());
                }
            }
        }
        return found;
    }

    private boolean canEnterRegion(Region from, Region to) {
        if (from.pos().equals(to.pos())) {
            return true;
        }
        long distance = Math.abs((long) from.pos().x() - to.pos().x()) + Math.abs((long) from.pos().z() - to.pos().z());
        if (distance != 1) {
            return false;
        }
        return (from.type() == RegionType.INLAND && to.type() == RegionType.FLUVIAL)
            || (from.type() == RegionType.FLUVIAL && to.type() == RegionType.COASTAL);
    }

    List<CellPos> predecessors(CellPos target) {
        return incoming.computeIfAbsent(target, this::findPredecessors);
    }

    private List<CellPos> findPredecessors(CellPos target) {
        if (target.getRegion().containsOutOfBoundsCells()) {
            return List.of();
        }
        routing(target.getRegion());
        if (isDivide(target)) {
            return List.of();
        }
        List<CellPos> predecessors = new ArrayList<>();
        for (FlowDirection direction : FlowDirection.D8) {
            CellPos previous = target.relative(direction);
            if (steps(previous).stream().noneMatch(step -> step.pos().equals(target))) {
                continue;
            }
            if (!isDivide(previous)) {
                predecessors.add(previous);
            }
        }
        return predecessors;
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

    boolean isDivide(CellPos pos) {
        return cell(pos).feature() == Feature.DIVIDE;
    }

    private boolean isTerminus(CellPos pos) {
        return routing(pos.getRegion()).terminusCells().contains(pos);
    }

    List<Step> steps(CellPos pos) {
        return moves.computeIfAbsent(pos, key -> {
            Routing local = routing(key.getRegion());
            if (!local.allows(key) || local.termini().isEmpty() || isTerminus(key)) {
                return List.of();
            }
            return candidates(key, nearestTerminus(key), local);
        });
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
        CellData cell = cell(current);
        for (FlowDirection direction : cell.flowDirections()) {
            CellPos next = current.relative(direction);
            if (local.allows(next)) {
                candidates.put(next, new Step(next, 0, distanceSquared(next, target) < distance));
            }
        }

        if (enableNudging) {
            int currentHeight = cell.height();
            FlowDirection toward = FlowDirection.toward(current, target);
            for (FlowDirection direction : cell.flowDirections()) {
                CellPos next = current.relative(direction.rotateToward(toward));
                if (candidates.containsKey(next) || !makesProgress(next, target, distance, local)) {
                    continue;
                }
                long rise = (long) cell(next).height() - currentHeight;
                if (rise <= nudgingLimit) {
                    candidates.put(next, new Step(next, Math.max(0, rise), true));
                }
            }
        }
        if (distance <= fallbackDistanceSquared) {
            CellPos direct = forceTowardTerminus(current, target, distance, local);
            if (direct != null) {
                candidates.putIfAbsent(direct, new Step(direct, 0, true));
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
        return local.allows(next) && distanceSquared(next, target) < distance;
    }

    private static long distanceSquared(CellPos from, CellPos to) {
        long dx = (long) to.x() - from.x();
        long dz = (long) to.z() - from.z();
        return dx * dx + dz * dz;
    }
}
