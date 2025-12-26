package org.sosly.rivertale.density;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.core.Cache;
import org.sosly.rivertale.metric.Store;

public class SampleCache implements Cache<Sample> {
    private static final int DEFAULT_CAPACITY = 1_000_000;
    private static final int SAMPLE_Y = 63;

    private static volatile SampleCache instance;

    private final Map<Long, Sample> cache;
    private final int capacity;
    private final SampleProvider sampler;

    private SampleCache(SampleProvider sampler, int capacity) {
        this.capacity = capacity;
        this.sampler = sampler;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Sample> eldest) {
                return size() > SampleCache.this.capacity;
            }
        });
    }

    public static synchronized void init(SampleProvider sampler) {
        if (instance != null) {
            return;
        }
        instance = new SampleCache(sampler, DEFAULT_CAPACITY);
    }

    public static SampleCache get() {
        return instance;
    }

    public static boolean isInitialized() {
        return instance != null;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.clear();
            instance = null;
        }
    }

    @Override
    public Sample getOrCompute(int x, int z) {
        ChunkPos chunk = new ChunkPos(new BlockPos(x, 0, z));
        long key = chunk.toLong();

        Sample cached = cache.get(key);
        if (cached != null) {
            Store.getRatio(SampleCache.class, "hits").success();
            return cached;
        }

        Store.getRatio(SampleCache.class, "hits").failure();

        Sample value = sampler.sample(chunk);
        cache.put(key, value);
        return value;
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
