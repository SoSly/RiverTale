package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import org.sosly.rivertale.core.CellPos;

final class LakeFill {
    private final Function<CellPos, List<CellPos>> neighbors;
    private final ToIntFunction<CellPos> height;

    LakeFill(Function<CellPos, List<CellPos>> neighbors, ToIntFunction<CellPos> height) {
        this.neighbors = neighbors;
        this.height = height;
    }

    Optional<Spill> search(CellPos entry, int ceiling, int maxCells, Predicate<Spill> drains) {
        return resolve(entry, ceiling, maxCells, drains).spill();
    }

    Result resolve(CellPos entry, int ceiling, int maxCells, Predicate<Spill> drains) {
        int level = height.applyAsInt(entry);
        Map<CellPos, CellPos> parents = new LinkedHashMap<>();
        parents.put(entry, entry);
        while (level <= ceiling && parents.size() <= maxCells) {
            Map<CellPos, CellPos> rim = rim(parents);
            if (rim.isEmpty()) {
                break;
            }
            int next = rim.keySet().stream().mapToInt(height).min().orElseThrow();
            level = Math.max(level, next);
            if (level > ceiling) {
                level = ceiling;
                break;
            }
            for (CellPos lip : rim.keySet()) {
                if (height.applyAsInt(lip) > level) {
                    continue;
                }
                Spill spill = spill(parents, rim.get(lip), lip, level);
                if (drains.test(spill)) {
                    return new Result(Optional.of(spill), Map.of());
                }
            }
            int surface = level;
            long additions = rim.keySet().stream().filter(pos -> height.applyAsInt(pos) <= surface).count();
            if (parents.size() + additions > maxCells) {
                break;
            }
            for (Map.Entry<CellPos, CellPos> edge : rim.entrySet()) {
                if (height.applyAsInt(edge.getKey()) <= level) {
                    parents.put(edge.getKey(), edge.getValue());
                }
            }
        }
        return new Result(Optional.empty(), depths(parents, level));
    }

    private Map<CellPos, CellPos> rim(Map<CellPos, CellPos> parents) {
        Map<CellPos, CellPos> rim = new LinkedHashMap<>();
        for (CellPos cell : parents.keySet()) {
            for (CellPos neighbor : neighbors.apply(cell)) {
                if (!parents.containsKey(neighbor)) {
                    rim.putIfAbsent(neighbor, cell);
                }
            }
        }
        return rim;
    }

    private Spill spill(Map<CellPos, CellPos> parents, CellPos bank, CellPos lip, int level) {
        List<CellPos> route = new ArrayList<>();
        route.add(lip);
        CellPos cursor = bank;
        while (!cursor.equals(parents.get(cursor))) {
            route.add(cursor);
            cursor = parents.get(cursor);
        }
        route.add(cursor);
        Collections.reverse(route);
        return new Spill(List.copyOf(route), depths(parents, level));
    }

    private Map<CellPos, Integer> depths(Map<CellPos, CellPos> parents, int level) {
        Map<CellPos, Integer> depths = new HashMap<>();
        for (CellPos cell : parents.keySet()) {
            depths.put(cell, level - height.applyAsInt(cell));
        }
        return Map.copyOf(depths);
    }

    record Result(Optional<Spill> spill, Map<CellPos, Integer> closed) {
    }

    record Spill(List<CellPos> route, Map<CellPos, Integer> overFill) {
        CellPos lip() {
            return route.get(route.size() - 1);
        }
    }
}
