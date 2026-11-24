package org.sosly.rivertale.region;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;

public class RegionCache {

    private static volatile RegionCache instance;

    private final Map<Long, Region> cache;
    private final int capacity;

    private RegionCache(int capacity) {
        this.capacity = capacity;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Region> eldest) {
                return size() > RegionCache.this.capacity;
            }
        });
    }

    public static synchronized void init() {
        if (instance != null) {
            return;
        }
        instance = new RegionCache(CommonConfig.get().caches().regions());
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

    public Region getOrCompute(RegionPos pos) {
        long key = pos.toLong();
        Region cached = cache.get(key);

        if (cached != null) {
            Store.getRatio(RegionCache.class, "hits").success();
            return cached;
        }

        Store.getRatio(RegionCache.class, "hits").failure();

        Region value = Region.create(pos, CellCache.get(), SampleCache.get());
        cache.put(key, value);

        return value;
    }

    public void put(Region region) {
        cache.put(region.pos().toLong(), region);
    }

    public void clear() {
        cache.clear();
    }
}
