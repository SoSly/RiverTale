package org.sosly.rivertale.world;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.river.WatershedCache;

public class RiverShaping {
    private static Holder<Biome> riverBiome;

    private RiverShaping() {}

    public static void init(Holder<Biome> river) {
        riverBiome = river;
    }

    public static void shutdown() {
        riverBiome = null;
    }

    public static Holder<Biome> getRiverBiome() {
        return riverBiome;
    }

    public static void generateRiverMap(ChunkAccess chunk) {
        Timer.Record timer = Store.getTimer(RiverShaping.class, "generateRiverMap").start();
        RegionPos center = new RegionPos(chunk.getPos().getMiddleBlockPosition(0));
        CellCache cellCache = CellCache.get();
        SampleCache sampleCache = SampleCache.get();
        RegionCache regionCache = RegionCache.get();

        Set<RegionPos> loadedRegions = new HashSet<>();

        regionCache.getOrCompute(center, cellCache, sampleCache);
        loadedRegions.add(center);

        for (FlowDirection dir : FlowDirection.D8) {
            RegionPos neighbor = center.relative(dir);
            regionCache.getOrCompute(neighbor, cellCache, sampleCache);
            loadedRegions.add(neighbor);
        }

        WatershedCache.get().finalizeReady(loadedRegions);
        timer.stop();
    }
}
