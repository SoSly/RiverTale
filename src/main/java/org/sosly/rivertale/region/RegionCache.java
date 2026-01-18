package org.sosly.rivertale.region;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.core.Cache;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.river.WatershedCache;

public class RegionCache implements Cache<Region> {
    private static final int DEFAULT_CAPACITY = 100;

    private static volatile RegionCache instance;

    private final Map<Long, Region> cache;
    private final int capacity;

    private RegionCache(int capacity) {
        this.capacity = capacity;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Region> eldest) {
                if (size() > RegionCache.this.capacity) {
                    WatershedCache watershedCache = WatershedCache.get();
                    if (watershedCache != null) {
                        watershedCache.evictForRegion(new RegionPos(eldest.getKey()));
                    }
                    return true;
                }
                return false;
            }
        });
    }

    public static synchronized void init() {
        if (instance != null) {
            return;
        }
        instance = new RegionCache(DEFAULT_CAPACITY);
    }

    public static RegionCache get() {
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.clear();
            instance = null;
        }
    }

    @Override
    public Region getOrCompute(int x, int z) {
        return getOrCompute(new RegionPos(x, z), CellCache.get(), SampleCache.get());
    }

    public Region getOrCompute(RegionPos pos, CellCache cellCache, SampleCache sampleCache) {
        long key = pos.toLong();
        Region cached = cache.get(key);

        if (cached != null) {
            Store.getRatio(RegionCache.class, "hits").success();
            return cached;
        }

        Store.getRatio(RegionCache.class, "hits").failure();

        Region value = Region.createSkeleton(pos, cellCache, sampleCache);
        cache.put(key, value);

        return value;
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
