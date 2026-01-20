package org.sosly.rivertale.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionExplorer;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.Basins;
import org.sosly.rivertale.terrain.Oceans;

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

        CellCache cellCache = CellCache.get();
        Set<Region> enrichedSet = new HashSet<>();

        for (Region region : workingSet) {
            List<Boundary> boundaries = new ArrayList<>();

            if (region.type() == RegionType.COASTAL) {
                Boundary oceanBoundary = Oceans.boundaries(region.pos(), cellCache);
                if (oceanBoundary != null) {
                    boundaries.add(oceanBoundary);
                }
            }

            if (region.type() == RegionType.COASTAL || region.type() == RegionType.FLUVIAL) {
                Boundary basinBoundary = Basins.boundaries(region.pos(), cellCache);
                if (basinBoundary != null) {
                    boundaries.add(basinBoundary);
                }
            }

            Region enriched = region.withBoundaries(boundaries);
            RegionCache.get().put(enriched);
            enrichedSet.add(enriched);
        }

        // Future phases will add:
        // - Feature Classification
        // - Flowline Tracing
        // - Watershed Building
        timer.stop();
    }
}
