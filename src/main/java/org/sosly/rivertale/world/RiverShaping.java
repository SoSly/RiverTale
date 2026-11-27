package org.sosly.rivertale.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.cell.feature.FeatureHandler;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionTypeCache;
import org.sosly.rivertale.river.PreparedRiverData.CellData;
import org.sosly.rivertale.river.RiverRegion;
import org.sosly.rivertale.river.RiverRegionCache;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.CarveProposal;

public class RiverShaping {
    private static volatile ActiveWorld world;

    private RiverShaping() {}

    public static void init(int waterLevel, RandomState state, long meanderSeed) {
        world = new ActiveWorld(state, waterLevel, meanderSeed, new RiverRegionCache());
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

        ChunkPos pos = chunk.getPos();
        if (new RegionPos(pos.getMiddleBlockPosition(0)).containsOutOfBoundsCells()) {
            return;
        }

        List<CarveProposal> proposals = carveNearby(pos, CommonConfig.get().carvingSearchRadius(), active.meanderSeed(),
            active.rivers()::getOrBuild, feature -> feature.handler);
        if (!proposals.isEmpty()) {
            ChunkCarving.apply(chunk, pos, proposals);
        }
    }

    static List<CarveProposal> carveNearby(ChunkPos chunk, int radius, long meanderSeed,
                                          Function<RegionPos, RiverRegion> regions, Function<Feature, FeatureHandler> handlers) {
        CellPos min = new CellPos(new BlockPos(chunk.getMinBlockX() - radius, 0, chunk.getMinBlockZ() - radius));
        CellPos max = new CellPos(new BlockPos(chunk.getMaxBlockX() + radius, 0, chunk.getMaxBlockZ() + radius));
        RegionPos first = min.getRegion();
        RegionPos last = max.getRegion();
        List<CarveProposal> proposals = new ArrayList<>();
        for (int x = first.x(); x <= last.x(); x++) {
            for (int z = first.z(); z <= last.z(); z++) {
                RegionPos pos = new RegionPos(x, z);
                if (pos.containsOutOfBoundsCells()) {
                    continue;
                }
                carveRegion(regions.apply(pos), min, max, chunk, meanderSeed, handlers, proposals);
            }
        }
        return proposals;
    }

    private static void carveRegion(RiverRegion region, CellPos min, CellPos max, ChunkPos chunk, long meanderSeed,
                                    Function<Feature, FeatureHandler> handlers, List<CarveProposal> proposals) {
        if (region.watersheds().isEmpty()) {
            return;
        }
        CellPos start = region.region().pos().getMinCell();
        int side = CommonConfig.get().cellsPerRegion();
        int maxX = Math.min(max.x(), start.x() + side - 1);
        int maxZ = Math.min(max.z(), start.z() + side - 1);
        for (int x = Math.max(min.x(), start.x()); x <= maxX; x++) {
            for (int z = Math.max(min.z(), start.z()); z <= maxZ; z++) {
                CellPos pos = new CellPos(x, z);
                CellData data = region.cells().get(pos);
                if (data == null || data.feature() == Feature.NONE || data.feature() == Feature.DIVIDE) {
                    continue;
                }
                Watershed watershed = region.watershedAt(pos);
                if (watershed != null) {
                    proposals.addAll(handlers.apply(data.feature()).carve(data.cell(), watershed, chunk, meanderSeed));
                }
            }
        }
    }

    public static void prepareNearby(RegionPos center, RandomState state) {
        ActiveWorld active = activeWorld(state);
        if (active != null) {
            active.rivers().prepareNearby(center);
        }
    }

    public static boolean rebuild(RegionPos center, RandomState state, CommonConfig tuning) {
        ActiveWorld active = activeWorld(state);
        if (active == null) {
            return false;
        }
        active.rivers().rebuild(center, () -> {
            CommonConfig.set(tuning);
            clearTerrainCaches();
        });
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

    private record ActiveWorld(RandomState state, int seaLevel, long meanderSeed, RiverRegionCache rivers) {
    }
}
