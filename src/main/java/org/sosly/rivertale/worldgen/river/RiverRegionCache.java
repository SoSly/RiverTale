package org.sosly.rivertale.worldgen.river;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class RiverRegionCache {
    private static final ConcurrentHashMap<RiverRegionKey, RiverRegion> CELLS = new ConcurrentHashMap<>();

    public static RiverRegion getOrCompute(RiverRegionKey key, Function<RiverRegionKey, RiverRegion> compute) {
        return CELLS.computeIfAbsent(key, compute);
    }

    public static RiverRegion getIfPresent(RiverRegionKey key) {
        return CELLS.get(key);
    }

    public static void clear() {
        CELLS.clear();
    }
}
