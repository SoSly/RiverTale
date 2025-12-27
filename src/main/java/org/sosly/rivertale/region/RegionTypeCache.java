package org.sosly.rivertale.region;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.sosly.rivertale.core.Cache;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;

public class RegionTypeCache implements Cache<RegionType> {
    private static final int DEFAULT_CAPACITY = 100;

    private static volatile RegionTypeCache instance;

    private final Map<Long, RegionType> cache;
    private final int capacity;

    private RegionTypeCache(int capacity) {
        this.capacity = capacity;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, RegionType> eldest) {
                return size() > RegionTypeCache.this.capacity;
            }
        });
    }

    public static synchronized void init() {
        if (instance != null) {
            return;
        }
        instance = new RegionTypeCache(DEFAULT_CAPACITY);
    }

    public static RegionTypeCache get() {
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.clear();
            instance = null;
        }
    }

    @Override
    public RegionType getOrCompute(int x, int z) {
        return getOrCompute(new RegionPos(x, z), SampleCache.get());
    }

    public RegionType getOrCompute(RegionPos pos, SampleCache sampleCache) {
        long key = pos.toLong();
        RegionType cached = cache.get(key);

        if (cached != null) {
            Store.getRatio(RegionTypeCache.class, "hits").success();
            return cached;
        }

        Store.getRatio(RegionTypeCache.class, "hits").failure();

        RegionType value = RegionType.classify(pos, sampleCache);
        cache.put(key, value);

        return value;
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
