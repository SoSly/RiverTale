package org.sosly.rivertale.poc.vis;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.poc.vis.metrics.Store;
import org.sosly.rivertale.poc.vis.metrics.Timer;

public class SampleCache {
    private static final int SAMPLE_Y = 63;

    private final int capacity;
    private final Map<Long, Sample> cache;

    public SampleCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, false){
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Sample> eldest) {
                return size() > SampleCache.this.capacity;
            }
        };
    }

    public Sample getOrCompute(int x, int z, RandomState random, BiomeSource biomes) {
        long key = Sample.asLong(x, z);
        Sample cached = cache.get(key);

        if (cached != null) {
            Store.getRatio(SampleCache.class, "hits").success();
            return cached;
        }

        Store.getRatio(SampleCache.class, "hits").failure();
        Timer.Record record = Store.getTimer(SampleCache.class, "sample").start();

        NoiseRouter router = random.router();
        Climate.Sampler sampler = random.sampler();
        DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(x, SAMPLE_Y, z);

        double continents = router.continents().compute(ctx);
        double depth = router.depth().compute(ctx);
        double erosion = router.erosion().compute(ctx);
        double ridges = router.ridges().compute(ctx);
        double temperature = router.temperature().compute(ctx);
        double vegetation = router.vegetation().compute(ctx);

        Sample value = new Sample(x, z,
            biomes.getNoiseBiome(x >> 2, SAMPLE_Y >> 2, z >> 2, sampler).value(),
            continents,
            depth,
            erosion,
            ridges,
            temperature,
            vegetation
        );
        record.stop();
        cache.put(key, value);

        return value;
    }

    public void clear() {
        cache.clear();
    }
}
