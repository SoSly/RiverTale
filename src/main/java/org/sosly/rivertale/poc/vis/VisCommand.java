package org.sosly.rivertale.poc.vis;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.cell.features.Feature;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.network.RegionPacket;
import org.sosly.rivertale.network.RiverTaleNetwork;
import org.sosly.rivertale.path.FlowCalculator;
import org.sosly.rivertale.poc.vis.metrics.Store;
import org.sosly.rivertale.poc.vis.metrics.Timer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.UUID;

public class VisCommand {

    private static final Set<UUID> ENABLED_PLAYERS = new HashSet<>();
    private static final RegionCache REGION_CACHE = new RegionCache(100);

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("vis")
            .executes(context -> {
                CommandSourceStack source = context.getSource();

                if (!(source.getEntity() instanceof ServerPlayer player)) {
                    source.sendFailure(Component.literal("This command must be run by a player"));
                    return 0;
                }

                UUID playerId = player.getUUID();

                if (ENABLED_PLAYERS.contains(playerId)) {
                    ENABLED_PLAYERS.remove(playerId);
                    RiverTaleNetwork.sendToPlayer(new RegionPacket(new ArrayList<>(), new HashMap<>(), 0, 0), player);
                    source.sendSuccess(() -> Component.literal("Vis disabled")
                        .withStyle(ChatFormatting.YELLOW), false);
                    return 1;
                }

                ENABLED_PLAYERS.add(playerId);
                sendDataToPlayer(player);
                source.sendSuccess(() -> Component.literal("Vis enabled")
                    .withStyle(ChatFormatting.GREEN), false);
                return 1;
            });
    }

    public static boolean isEnabled(UUID playerId) {
        return ENABLED_PLAYERS.contains(playerId);
    }

    public static long getPlayerRegionKey(ServerPlayer player) {
        if (!isEnabled(player.getUUID())) {
            return 0;
        }
        int regionSize = RiverConfig.REGION_SIZE.get();
        BlockPos pos = player.blockPosition();
        int regionX = Math.floorDiv(pos.getX(), regionSize);
        int regionZ = Math.floorDiv(pos.getZ(), regionSize);
        return ((long) regionX << 32) | (regionZ & 0xFFFFFFFFL);
    }

    public static void sendDataToPlayer(ServerPlayer player) {
        if (!isEnabled(player.getUUID())) {
            return;
        }
        int regionSize = RiverConfig.REGION_SIZE.get();

        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        RandomState randomState = level.getChunkSource().randomState();
        BiomeSource biomeSource = level.getChunkSource().getGenerator().getBiomeSource();

        Store.clear();
        SampleCache cache = new SampleCache(100000);
        OceanFinder finder = new OceanFinder(cache, randomState, biomeSource, regionSize);

        int centerRegionX = Math.floorDiv(pos.getX(), regionSize);
        int centerRegionZ = Math.floorDiv(pos.getZ(), regionSize);

        Map<Long, RegionType> classifications = new HashMap<>();
        Set<Long> coastalRegions = new HashSet<>();
        Set<Long> fluvialRegions = new HashSet<>();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int rx = centerRegionX + dx;
                int rz = centerRegionZ + dz;
                long key = regionKey(rx, rz);
                RegionType type = classifyRegion(finder, rx, rz, regionSize);
                classifications.put(key, type);
                if (type == RegionType.COASTAL) {
                    coastalRegions.add(key);
                } else if (type == RegionType.INLAND) {
                    fluvialRegions.add(key);
                }
            }
        }

        int[][] cardinals = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        for (long inlandKey : new HashSet<>(fluvialRegions)) {
            int fx = (int) (inlandKey >> 32);
            int fz = (int) inlandKey;
            for (int[] offset : cardinals) {
                int nx = fx + offset[0];
                int nz = fz + offset[1];
                long neighborKey = regionKey(nx, nz);
                if (!classifications.containsKey(neighborKey)) {
                    RegionType neighborType = classifyRegion(finder, nx, nz, regionSize);
                    classifications.put(neighborKey, neighborType);
                    if (neighborType == RegionType.COASTAL) {
                        coastalRegions.add(neighborKey);
                        classifications.put(inlandKey, RegionType.FLUVIAL);
                    }
                } else if (classifications.get(neighborKey) == RegionType.COASTAL) {
                    classifications.put(inlandKey, RegionType.FLUVIAL);
                }
            }
        }

        LoadedRegions loaded = new LoadedRegions();
        List<Region> regions = new ArrayList<>();
        int totalBoundaries = 0;
        int cellsPerRegion = RiverConfig.CELLS_PER_REGION.get();

        for (Map.Entry<Long, RegionType> entry : classifications.entrySet()) {
            long key = entry.getKey();
            int rx = (int) (key >> 32);
            int rz = (int) key;
            RegionType type = entry.getValue();

            Region region;
            Grid flow = null;

            if (type == RegionType.COASTAL) {
                Region cached = REGION_CACHE.get(key);
                if (cached != null) {
                    region = cached;
                } else {
                    RegionBuilder builder = new RegionBuilder(finder, rx, rz, regionSize);
                    region = builder.build(type);
                    REGION_CACHE.put(key, region);
                }
                totalBoundaries += region.size();
            } else {
                region = new Region(rx, rz, type);
            }

            if (type == RegionType.COASTAL || type == RegionType.FLUVIAL) {
                RegionPos regionPos = new RegionPos(rx, rz);
                flow = FlowCalculator.compute(regionPos, cellsPerRegion, cache, randomState, biomeSource);
                classifyCells(flow, rx, rz, regionSize, cellsPerRegion, cache, randomState, biomeSource);
            }

            loaded.put(rx, rz, region, flow);
            regions.add(region);
        }

        RiverTaleNetwork.sendToPlayer(
            new RegionPacket(regions, loaded.flows(), regionSize, cellsPerRegion),
            player);

        Timer buildTiming = Store.getTimer(RegionBuilder.class, "build");
        player.sendSystemMessage(Component.literal(String.format(
            "Vis: %d regions, %d coastal, %d boundaries, %d flows, %.1fms",
            regions.size(), coastalRegions.size(), totalBoundaries, loaded.flowCount(),
            buildTiming.total() / 1000.0))
            .withStyle(ChatFormatting.GRAY));
    }

    private static final double SNOWMELT_THRESHOLD = 1.25;

    private static void classifyCells(Grid flow, int regionX, int regionZ, int regionSize, int cellsPerRegion,
                                      SampleCache cache, RandomState randomState,
                                      net.minecraft.world.level.biome.BiomeSource biomeSource) {
        int cellSize = regionSize / cellsPerRegion;
        int baseX = regionX * regionSize;
        int baseZ = regionZ * regionSize;

        for (int row = 0; row < cellsPerRegion; row++) {
            for (int col = 0; col < cellsPerRegion; col++) {
                int worldX = baseX + col * cellSize + cellSize / 2;
                int worldZ = baseZ + row * cellSize + cellSize / 2;

                Sample sample = cache.getOrCompute(worldX, worldZ, randomState, biomeSource);
                double score = sample.continents() + sample.depth();

                if (score > SNOWMELT_THRESHOLD) {
                    flow.get(row, col).setFeature(Feature.SNOWMELT);
                }
            }
        }
    }

    private static RegionType classifyRegion(OceanFinder finder, int regionX, int regionZ, int regionSize) {
        int minChunkX = (regionX * regionSize) >> 4;
        int maxChunkX = ((regionX + 1) * regionSize) >> 4;
        int minChunkZ = (regionZ * regionSize) >> 4;
        int maxChunkZ = ((regionZ + 1) * regionSize) >> 4;

        int gridWidth = maxChunkX - minChunkX;
        int gridHeight = maxChunkZ - minChunkZ;
        int cellWidth = gridWidth / 4;
        int cellHeight = gridHeight / 4;

        int oceanCount = 0;
        int landCount = 0;

        for (int gx = 0; gx < 4; gx++) {
            for (int gz = 0; gz < 4; gz++) {
                int sampleX = minChunkX + (gx * cellWidth) + (cellWidth / 2);
                int sampleZ = minChunkZ + (gz * cellHeight) + (cellHeight / 2);

                if (finder.isOceanChunk(sampleX, sampleZ)) {
                    oceanCount++;
                } else {
                    landCount++;
                }
            }
        }

        if (landCount == 0) {
            return RegionType.OCEAN;
        }
        if (oceanCount == 0) {
            return RegionType.INLAND;
        }
        return RegionType.COASTAL;
    }

    private static long regionKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    public static void disable(UUID playerId) {
        ENABLED_PLAYERS.remove(playerId);
    }

    public static void disableAll() {
        ENABLED_PLAYERS.clear();
    }
}
