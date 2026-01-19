package org.sosly.rivertale.world;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionExplorer;

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

    public static void shape(ChunkAccess chunk) {
        Timer.Record timer = Store.getTimer(RiverShaping.class, "shape").start();
        BlockPos center = chunk.getPos().getMiddleBlockPosition(0);

        Set<Region> workingSet = RegionExplorer.discover(center);
        if (workingSet.isEmpty()) {
            timer.stop();
            return;
        }

        // Future phases will add:
        // - Boundary Identification
        // - Feature Classification
        // - Flowline Tracing
        // - Watershed Building
        timer.stop();
    }
}
