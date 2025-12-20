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
import org.sosly.rivertale.network.VisualizeD8Packet.D8CellData;
import org.sosly.rivertale.worldgen.river.CellClassification;
import org.sosly.rivertale.worldgen.river.CellDensityProvider;
import org.sosly.rivertale.worldgen.river.D8FlowCalculator;
import org.sosly.rivertale.worldgen.river.D8FlowResult;
import org.sosly.rivertale.worldgen.river.D8PathRefiner;
import org.sosly.rivertale.worldgen.river.FlowDirection;
import org.sosly.rivertale.worldgen.river.RiverCellKey;
import org.sosly.rivertale.worldgen.river.CellSampleDensityCache;
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
    private static final int CELL_RADIUS = 2;

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
                sendCellDataToPlayer(player);
                long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
                int totalCells = (CELL_RADIUS * 2 + 1) * (CELL_RADIUS * 2 + 1);
                double msPerCell = (double) elapsedMs / totalCells;
                source.sendSuccess(() -> Component.literal(
                    String.format("River visualization enabled (computed in %dms for %d cells, %.2fms/cell)",
                        elapsedMs, totalCells, msPerCell))
                    .withStyle(ChatFormatting.GREEN), false);
                return 1;
            });
    }

    public static boolean isVisualizationEnabled(UUID playerId) {
        return ENABLED_PLAYERS.contains(playerId);
    }

    public static void sendCellDataToPlayer(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        RandomState randomState = level.getChunkSource().randomState();

        CellDensityProvider provider = new CellDensityProvider(randomState);
        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        CellSampleDensityCache densityCache = new CellSampleDensityCache();
        CellSampleDensityCache continentsCache = new CellSampleDensityCache();
        CellSampleDensityCache depthCache = new CellSampleDensityCache();

        BiFunction<Integer, Integer, Double> cachedDensitySampler = (x, z) ->
            densityCache.getOrCompute(x, z, provider::getDensity);

        BiFunction<Integer, Integer, Double> cachedContinentsSampler = (x, z) ->
            continentsCache.getOrCompute(x, z, provider::getContinents);

        BiFunction<Integer, Integer, Double> cachedDepthSampler = (x, z) ->
            depthCache.getOrCompute(x, z, provider::getDepth);

        RiverCellKey playerCell = RiverCellKey.fromBlockPos(pos.getX(), pos.getZ());

        Map<RiverCellKey, FlowDirection[][]> flowDataMap = new HashMap<>();
        Map<RiverCellKey, CellClassification> classificationMap = new HashMap<>();

        int outerRadius = CELL_RADIUS + 1;
        for (int dx = -outerRadius; dx <= outerRadius; dx++) {
            for (int dz = -outerRadius; dz <= outerRadius; dz++) {
                RiverCellKey key = new RiverCellKey(playerCell.cellX() + dx, playerCell.cellZ() + dz);

                FlowDirection[][] flowDirections = D8FlowCalculator.computeFlowDirections(key, cachedDensitySampler);
                CellClassification classification = D8FlowCalculator.classifyCell(
                    key, cachedContinentsSampler, cachedDepthSampler, oceanThreshold, lakeThreshold);

                flowDataMap.put(key, flowDirections);
                classificationMap.put(key, classification);
            }
        }

        List<D8CellData> cellDataList = new ArrayList<>();

        for (int dx = -CELL_RADIUS; dx <= CELL_RADIUS; dx++) {
            for (int dz = -CELL_RADIUS; dz <= CELL_RADIUS; dz++) {
                RiverCellKey key = new RiverCellKey(playerCell.cellX() + dx, playerCell.cellZ() + dz);

                FlowDirection[][] flowDirection = flowDataMap.get(key);
                CellClassification classification = classificationMap.get(key);

                FlowDirection[][][] neighborFlowDirections = new FlowDirection[4][][];
                CellClassification[] neighborClassifications = new CellClassification[4];

                RiverCellKey northKey = new RiverCellKey(key.cellX(), key.cellZ() - 1);
                RiverCellKey southKey = new RiverCellKey(key.cellX(), key.cellZ() + 1);
                RiverCellKey eastKey = new RiverCellKey(key.cellX() + 1, key.cellZ());
                RiverCellKey westKey = new RiverCellKey(key.cellX() - 1, key.cellZ());

                neighborFlowDirections[0] = flowDataMap.get(northKey);
                neighborFlowDirections[1] = flowDataMap.get(southKey);
                neighborFlowDirections[2] = flowDataMap.get(eastKey);
                neighborFlowDirections[3] = flowDataMap.get(westKey);

                neighborClassifications[0] = classificationMap.get(northKey);
                neighborClassifications[1] = classificationMap.get(southKey);
                neighborClassifications[2] = classificationMap.get(eastKey);
                neighborClassifications[3] = classificationMap.get(westKey);

                D8FlowResult d8Result = D8PathRefiner.refine(
                    key, flowDirection, classification,
                    neighborFlowDirections, neighborClassifications,
                    cachedDensitySampler, cachedContinentsSampler, cachedDepthSampler,
                    oceanThreshold, lakeThreshold);

                cellDataList.add(new D8CellData(
                    key.cellX(), key.cellZ(),
                    classification, d8Result.isBasin(),
                    d8Result.crossings(),
                    d8Result.primaryOutputDirection(), d8Result.flowDirection(),
                    d8Result.terminusSubcells(), d8Result.riverPaths(),
                    d8Result.confluenceSubcells()
                ));
            }
        }

        RiverTaleNetwork.sendToPlayer(new VisualizeD8Packet(true, cellDataList), player);
    }

    public static RiverCellKey getPlayerCell(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return RiverCellKey.fromBlockPos(pos.getX(), pos.getZ());
    }

    public static void clearEnabledPlayers() {
        ENABLED_PLAYERS.clear();
    }
}
