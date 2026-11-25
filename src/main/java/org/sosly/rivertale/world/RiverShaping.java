package org.sosly.rivertale.world;

import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionTypeCache;
import org.sosly.rivertale.river.RiverRegion;
import org.sosly.rivertale.river.RiverRegionCache;
import org.sosly.rivertale.river.Watershed;

public class RiverShaping {
    private static volatile ActiveWorld world;

    private RiverShaping() {}

    public static void init(int waterLevel, RandomState state) {
        world = new ActiveWorld(state, waterLevel, new RiverRegionCache());
    }

    public static void shutdown() {
        ActiveWorld active = world;
        if (active != null) {
            active.rivers().shutdown();
            world = null;
        }
    }

    public static boolean isActive(RandomState state) {
        return activeWorld(state) != null;
    }

    public static int getSeaLevel() {
        ActiveWorld active = world;
        return active == null ? 63 : active.seaLevel();
    }

    public static RandomState getRandomState() {
        ActiveWorld active = world;
        return active == null ? null : active.state();
    }

    private static ActiveWorld activeWorld(RandomState state) {
        ActiveWorld active = world;
        if (state == null || active == null || state != active.state()) {
            return null;
        }
        return active;
    }

    public static void shape(ChunkAccess chunk, RandomState state) {
        ActiveWorld active = activeWorld(state);
        if (active == null) {
            return;
        }

        RegionPos pos = new RegionPos(chunk.getPos().getMiddleBlockPosition(0));
        if (pos.containsOutOfBoundsCells()) {
            return;
        }

        active.rivers().getOrBuild(pos);
    }

    public static void prepareNearby(RegionPos center, RandomState state) {
        ActiveWorld active = activeWorld(state);
        if (active != null) {
            active.rivers().prepareNearby(center);
        }
    }

    public static boolean rebuild(RegionPos center, RandomState state) {
        ActiveWorld active = activeWorld(state);
        if (active == null) {
            return false;
        }
        active.rivers().rebuild(center, RiverShaping::clearTerrainCaches);
        return true;
    }

    private static void clearTerrainCaches() {
        RegionCache.get().clear();
        RegionTypeCache.get().clear();
        CellCache.get().clear();
        SampleCache.get().clear();
    }

    public static RiverRegion getReady(RegionPos pos, RandomState state) {
        ActiveWorld active = activeWorld(state);
        return active == null ? null : active.rivers().getReady(pos);
    }

    public static Cell preclassify(CellPos pos) {
        return qualify(pos, null);
    }

    public static Cell qualify(CellPos pos, @Nullable Watershed watershed) {
        CellCache cache = CellCache.get();
        Cell cell = watershed == null ? cache.getOrComputeWithFlow(pos) : cache.getOrCompute(pos);
        if (cell.feature() != Feature.NONE) {
            return cell;
        }

        return cache.setFeatureIfNone(Feature.classify(cell, watershed));
    }

    private record ActiveWorld(RandomState state, int seaLevel, RiverRegionCache rivers) {
    }
}
