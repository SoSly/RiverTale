package org.sosly.rivertale.worldgen.river;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class RiverCellCache {
    private static final ConcurrentHashMap<RiverCellKey, RiverCell> CELLS = new ConcurrentHashMap<>();

    public static RiverCell getOrCompute(RiverCellKey key, Function<RiverCellKey, RiverCell> compute) {
        return CELLS.computeIfAbsent(key, compute);
    }

    public static RiverCell getIfPresent(RiverCellKey key) {
        return CELLS.get(key);
    }

    public static void clear() {
        CELLS.clear();
    }
}
