package org.sosly.rivertale.poc.vis;

import java.util.LinkedHashMap;
import java.util.Map;
import org.sosly.rivertale.poc.vis.metrics.Store;

public class RegionCache {

    private final int capacity;
    private final Map<Long, Region> cache;

    public RegionCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Region> eldest) {
                return size() > RegionCache.this.capacity;
            }
        };
    }

    public Region get(long key) {
        Region cached = cache.get(key);
        if (cached != null) {
            Store.getRatio(RegionCache.class, "hits").success();
            return cached;
        }
        Store.getRatio(RegionCache.class, "hits").failure();
        return null;
    }

    public void put(long key, Region region) {
        cache.put(key, region);
    }

    public boolean contains(long key) {
        return cache.containsKey(key);
    }

    public void clear() {
        cache.clear();
    }

    public int size() {
        return cache.size();
    }

    public static long key(int regionX, int regionZ) {
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }
}
