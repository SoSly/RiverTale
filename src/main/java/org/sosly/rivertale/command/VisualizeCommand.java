package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.network.RiverTaleNetwork;
import org.sosly.rivertale.network.VisualizeD8Packet;
import org.sosly.rivertale.network.VisualizeD8Packet.D8RegionData;
import org.sosly.rivertale.worldgen.river.RegionDensityProvider;
import org.sosly.rivertale.worldgen.river.RegionFeatureType;
import org.sosly.rivertale.worldgen.river.D8FlowCalculator;
import org.sosly.rivertale.worldgen.river.D8FlowResult;
import org.sosly.rivertale.worldgen.river.D8PathRefiner;
import org.sosly.rivertale.worldgen.river.FlowDirection;
import org.sosly.rivertale.worldgen.river.RiverRegionKey;
import org.sosly.rivertale.worldgen.river.RiverRegionManager;
import org.sosly.rivertale.config.RiverConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;

public class VisualizeCommand {

    private static final Set<UUID> ENABLED_PLAYERS = new HashSet<>();
    private static final int REGION_RADIUS = 2;

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("visualize")
            .executes(context -> {
                CommandSourceStack source = context.getSource();

                if (!(source.getEntity() instanceof ServerPlayer player)) {
                    source.sendFailure(Component.literal("This command must be run by a player"));
                    return 0;
                }

                UUID playerId = player.getUUID();

                if (ENABLED_PLAYERS.contains(playerId)) {
                    ENABLED_PLAYERS.remove(playerId);
                    RiverTaleNetwork.sendToPlayer(new VisualizeD8Packet(false, new ArrayList<>()), player);
                    source.sendSuccess(() -> Component.literal("River visualization disabled")
                        .withStyle(ChatFormatting.YELLOW), false);
                    return 1;
                }

                ENABLED_PLAYERS.add(playerId);
                long startTime = System.nanoTime();
                sendRegionDataToPlayer(player);
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                int totalRegions = (REGION_RADIUS * 2 + 1) * (REGION_RADIUS * 2 + 1);
                double msPerRegion = (double) elapsedMs / totalRegions;
                source.sendSuccess(() -> Component.literal(
                    String.format("River visualization enabled (computed in %dms for %d regions, %.2fms/region)",
                        elapsedMs, totalRegions, msPerRegion))
                    .withStyle(ChatFormatting.GREEN), false);
                return 1;
            });
    }

    public static boolean isVisualizationEnabled(UUID playerId) {
        return ENABLED_PLAYERS.contains(playerId);
    }

    public static void sendRegionDataToPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        RandomState randomState = level.getChunkSource().randomState();

        RegionDensityProvider provider = new RegionDensityProvider(randomState);
        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        BiFunction<Integer, Integer, Double> densitySampler = provider::getDensity;
        BiFunction<Integer, Integer, Double> continentsSampler = provider::getContinents;
        BiFunction<Integer, Integer, Double> depthSampler = provider::getDepth;

        RiverRegionKey playerRegion = RiverRegionKey.fromBlockPos(pos.getX(), pos.getZ());

        Map<RiverRegionKey, FlowDirection[][]> flowDataMap = new HashMap<>();
        Map<RiverRegionKey, RegionFeatureType> featureTypeMap = new HashMap<>();

        int outerRadius = REGION_RADIUS + 1;
        for (int dx = -outerRadius; dx <= outerRadius; dx++) {
            for (int dz = -outerRadius; dz <= outerRadius; dz++) {
                RiverRegionKey key = new RiverRegionKey(playerRegion.regionX() + dx, playerRegion.regionZ() + dz);

                FlowDirection[][] flowDirections = D8FlowCalculator.computeFlowDirections(key, densitySampler);
                RegionFeatureType terrain = RiverRegionManager.classifyTerrain(key, provider);
                RegionFeatureType featureType = terrain != null ? terrain : RegionFeatureType.DIVIDE;

                flowDataMap.put(key, flowDirections);
                featureTypeMap.put(key, featureType);
            }
        }

        List<D8RegionData> regionDataList = new ArrayList<>();

        for (int dx = -REGION_RADIUS; dx <= REGION_RADIUS; dx++) {
            for (int dz = -REGION_RADIUS; dz <= REGION_RADIUS; dz++) {
                RiverRegionKey key = new RiverRegionKey(playerRegion.regionX() + dx, playerRegion.regionZ() + dz);

                FlowDirection[][] flowDirection = flowDataMap.get(key);
                RegionFeatureType featureType = featureTypeMap.get(key);

                FlowDirection[][][] neighborFlowDirections = new FlowDirection[4][][];
                RegionFeatureType[] neighborFeatureTypes = new RegionFeatureType[4];

                RiverRegionKey northKey = new RiverRegionKey(key.regionX(), key.regionZ() - 1);
                RiverRegionKey southKey = new RiverRegionKey(key.regionX(), key.regionZ() + 1);
                RiverRegionKey eastKey = new RiverRegionKey(key.regionX() + 1, key.regionZ());
                RiverRegionKey westKey = new RiverRegionKey(key.regionX() - 1, key.regionZ());

                neighborFlowDirections[0] = flowDataMap.get(northKey);
                neighborFlowDirections[1] = flowDataMap.get(southKey);
                neighborFlowDirections[2] = flowDataMap.get(eastKey);
                neighborFlowDirections[3] = flowDataMap.get(westKey);

                neighborFeatureTypes[0] = featureTypeMap.get(northKey);
                neighborFeatureTypes[1] = featureTypeMap.get(southKey);
                neighborFeatureTypes[2] = featureTypeMap.get(eastKey);
                neighborFeatureTypes[3] = featureTypeMap.get(westKey);

                D8FlowResult d8Result = D8PathRefiner.refine(
                    key, flowDirection, featureType,
                    neighborFlowDirections, neighborFeatureTypes,
                    densitySampler, continentsSampler, depthSampler,
                    oceanThreshold, lakeThreshold);

                regionDataList.add(new D8RegionData(
                    key.regionX(), key.regionZ(),
                    featureType,
                    d8Result.crossings(),
                    d8Result.primaryOutputDirection(), d8Result.flowDirection(),
                    d8Result.terminusCells(), d8Result.riverPaths(),
                    d8Result.confluenceCells()
                ));
            }
        }

        RiverTaleNetwork.sendToPlayer(new VisualizeD8Packet(true, regionDataList), player);
    }

    public static RiverRegionKey getPlayerRegion(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return RiverRegionKey.fromBlockPos(pos.getX(), pos.getZ());
    }

    public static void clearEnabledPlayers() {
        ENABLED_PLAYERS.clear();
    }

    public static void removePlayer(UUID playerId) {
        ENABLED_PLAYERS.remove(playerId);
    }
}
