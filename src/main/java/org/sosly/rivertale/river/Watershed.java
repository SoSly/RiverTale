package org.sosly.rivertale.river;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import org.sosly.rivertale.core.CellPos;

public class Watershed {
    private final Map<CellPos, CellPos> downstream;
    private final Map<CellPos, Set<CellPos>> upstream;
    private final Map<CellPos, Integer> upstreamCountCache;
    private final Map<CellPos, Integer> elevations;
    private final Map<CellPos, Double> flows;

    private Watershed(CellPos terminus, Map<CellPos, CellPos> links) {
        downstream = new HashMap<>();
        upstream = new HashMap<>();
        upstreamCountCache = new HashMap<>();
        elevations = Map.of();
        flows = Map.of();
        if (links.containsKey(terminus) || links.values().stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("Only nonterminal downstream links are accepted");
        }
        downstream.putAll(links);
        downstream.put(terminus, null);
        for (Map.Entry<CellPos, CellPos> link : links.entrySet()) {
            if (!downstream.containsKey(link.getValue())) {
                throw new IllegalArgumentException("Every downstream cell must belong to the watershed");
            }
            upstream.computeIfAbsent(link.getValue(), key -> new HashSet<>()).add(link.getKey());
        }
        List<CellPos> ordered = outletToSources();
        if (ordered.size() != downstream.size()) {
            throw new IllegalArgumentException("Every watershed cell must drain to its terminus");
        }
        Collections.reverse(ordered);
        for (CellPos cell : ordered) {
            int count = 0;
            for (CellPos tributary : upstream(cell)) {
                count += 1 + upstreamCountCache.get(tributary);
            }
            upstreamCountCache.put(cell, count);
        }
    }

    private Watershed(Watershed topology, Map<CellPos, Integer> elevations, Map<CellPos, Double> flows) {
        downstream = topology.downstream;
        upstream = topology.upstream;
        upstreamCountCache = topology.upstreamCountCache;
        this.elevations = Map.copyOf(elevations);
        this.flows = Map.copyOf(flows);
    }

    public static Watershed fromLinks(CellPos terminus, Map<CellPos, CellPos> links) {
        return new Watershed(terminus, links);
    }

    Watershed withElevations(PreparedRiverData data, double riseRunRatio) {
        int floor = data.seaLevel() - 1;
        List<CellPos> ordered = outletToSources();
        Map<CellPos, Integer> assigned = terrainElevations(data, floor, ordered);
        if (riseRunRatio > 0) {
            applyMinimumGrade(assigned, floor, ordered, BigDecimal.valueOf(riseRunRatio));
        }
        return new Watershed(this, assigned, flows);
    }

    Watershed withFlowAccumulation(double sourceFlow, double flowPerCell) {
        List<CellPos> ordered = outletToSources();
        Map<CellPos, Double> assigned = new HashMap<>();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            CellPos cell = ordered.get(i);
            Double incoming = assigned.get(cell);
            double flow = incoming == null ? sourceFlow : incoming + flowPerCell;
            assigned.put(cell, flow);
            CellPos next = downstream(cell);
            if (next != null) {
                assigned.merge(next, flow, Double::sum);
            }
        }
        return new Watershed(this, elevations, assigned);
    }

    private Map<CellPos, Integer> terrainElevations(PreparedRiverData data, int floor, List<CellPos> ordered) {
        Map<CellPos, Integer> assigned = new HashMap<>();
        for (int i = ordered.size() - 1; i >= 0; i--) {
            CellPos cell = ordered.get(i);
            int cellY = Math.max(floor, data.cell(cell).height());
            for (CellPos tributary : upstream(cell)) {
                cellY = Math.min(cellY, assigned.get(tributary));
            }
            assigned.put(cell, cellY);
        }
        return assigned;
    }

    private void applyMinimumGrade(Map<CellPos, Integer> assigned, int floor, List<CellPos> ordered, BigDecimal ratio) {
        Grade sea = new Grade(floor, 0);
        Map<CellPos, Grade> grades = new HashMap<>();
        for (CellPos cell : ordered) {
            CellPos nextDownstream = downstream(cell);
            Grade downstreamGrade = nextDownstream == null ? sea : grades.get(nextDownstream);
            Grade grade = new Grade(downstreamGrade.anchorY(), downstreamGrade.steps() + 1);
            int minimumY = grade.minimumY(ratio);
            int terrainY = assigned.get(cell);
            if (terrainY > minimumY) {
                grades.put(cell, new Grade(terrainY, 0));
            } else {
                grades.put(cell, grade);
                assigned.put(cell, minimumY);
            }
        }
    }

    private record Grade(int anchorY, int steps) {
        int minimumY(BigDecimal ratio) {
            return ratio.multiply(BigDecimal.valueOf(steps)).setScale(0, RoundingMode.FLOOR)
                .add(BigDecimal.valueOf(anchorY)).intValueExact();
        }
    }

    public OptionalInt cellY(CellPos cell) {
        Integer elevation = elevations.get(cell);
        return elevation == null ? OptionalInt.empty() : OptionalInt.of(elevation);
    }

    public OptionalDouble flow(CellPos cell) {
        Double flow = flows.get(cell);
        return flow == null ? OptionalDouble.empty() : OptionalDouble.of(flow);
    }

    public List<CellPos> outletToSources() {
        List<CellPos> ordered = new ArrayList<>();
        ArrayDeque<CellPos> pending = new ArrayDeque<>();
        downstream.keySet().stream().filter(cell -> downstream.get(cell) == null)
            .sorted(Comparator.comparingInt(CellPos::x).thenComparingInt(CellPos::z)).forEach(pending::add);
        while (!pending.isEmpty()) {
            CellPos cell = pending.remove();
            ordered.add(cell);
            upstream(cell).stream().sorted(Comparator.comparingInt(CellPos::x).thenComparingInt(CellPos::z))
                .forEach(pending::add);
        }
        return ordered;
    }

    public Set<CellPos> upstream(CellPos cell) {
        return Collections.unmodifiableSet(upstream.getOrDefault(cell, Set.of()));
    }

    public CellPos downstream(CellPos cell) {
        return downstream.get(cell);
    }

    public int accumulation(CellPos cell) {
        return upstreamCountCache.getOrDefault(cell, 0);
    }

    public Set<CellPos> allCells() {
        return Collections.unmodifiableSet(downstream.keySet());
    }

    public boolean contains(CellPos cell) {
        return downstream.containsKey(cell);
    }

    public Optional<CellPos> terminus(CellPos cell) {
        if (!downstream.containsKey(cell)) {
            return Optional.empty();
        }

        CellPos current = cell;
        while (downstream.get(current) != null) {
            current = downstream.get(current);
        }
        return Optional.of(current);
    }
}
