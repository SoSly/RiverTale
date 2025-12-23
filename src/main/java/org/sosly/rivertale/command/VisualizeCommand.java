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
import org.sosly.rivertale.worldgen.river.D8FlowResult;
import org.sosly.rivertale.worldgen.river.RiverRegionKey;
import org.sosly.rivertale.worldgen.river.RiverRegionManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
        RiverRegionKey playerRegion = RiverRegionKey.fromBlockPos(pos.getX(), pos.getZ());

        List<D8RegionData> regionDataList = new ArrayList<>();

        for (int dx = -REGION_RADIUS; dx <= REGION_RADIUS; dx++) {
            for (int dz = -REGION_RADIUS; dz <= REGION_RADIUS; dz++) {
                RiverRegionKey key = new RiverRegionKey(playerRegion.regionX() + dx, playerRegion.regionZ() + dz);

                RegionFeatureType terrain = RiverRegionManager.classifyTerrain(key, provider);
                RegionFeatureType featureType = terrain != null ? terrain : RegionFeatureType.DIVIDE;

                D8FlowResult d8Result = RiverRegionManager.computeFlowResult(key, provider, randomState);

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
