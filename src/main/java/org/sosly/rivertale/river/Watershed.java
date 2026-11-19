package org.sosly.rivertale.river;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.sosly.rivertale.core.CellPos;

public class Watershed {
    private final Map<CellPos, CellPos> downstream = new HashMap<>();
    private final Map<CellPos, Set<CellPos>> upstream = new HashMap<>();
    private final Map<CellPos, Integer> upstreamCountCache = new HashMap<>();

    private Watershed(CellPos terminus, Map<CellPos, CellPos> links) {
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

    public static Watershed fromLinks(CellPos terminus, Map<CellPos, CellPos> links) {
        return new Watershed(terminus, links);
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
