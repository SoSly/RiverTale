package org.sosly.rivertale.worldgen.river;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

public class CellSampleDensityCache {

    private static final int DEFAULT_CAPACITY = 10000;

    private final int capacity;
    private final Map<Long, Double> cache;

    public CellSampleDensityCache() {
        this(DEFAULT_CAPACITY);
    }

    public CellSampleDensityCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, false) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Double> eldest) {
                return size() > CellSampleDensityCache.this.capacity;
            }
        };
    }

    public double getOrCompute(int x, int z, BiFunction<Integer, Integer, Double> computer) {
        long key = packKey(x, z);
        Double cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        double value = computer.apply(x, z);
        cache.put(key, value);
        return value;
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    private static long packKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
