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
import org.sosly.rivertale.path.Flow;
import org.sosly.rivertale.network.RiverTaleNetwork;
import org.sosly.rivertale.network.VisualizePacket;
import org.sosly.rivertale.network.VisualizePacket.D8RegionData;
import org.sosly.rivertale.terrain.RegionDensityProvider;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.path.Manager;

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
                    RiverTaleNetwork.sendToPlayer(new VisualizePacket(false, new ArrayList<>()), player);
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
        RegionPos playerRegion = RegionPos.at(pos.getX(), pos.getZ());

        List<D8RegionData> regionDataList = new ArrayList<>();

        for (int dx = -REGION_RADIUS; dx <= REGION_RADIUS; dx++) {
            for (int dz = -REGION_RADIUS; dz <= REGION_RADIUS; dz++) {
                RegionPos regionPos = new RegionPos(playerRegion.x() + dx, playerRegion.z() + dz);

                RegionType terrain = Manager.classifyTerrain(regionPos, provider);
                RegionType featureRegionType = terrain != null ? terrain : RegionType.DIVIDE;

                Flow d8Flow = Manager.computeFlowResult(regionPos, provider, randomState);

                regionDataList.add(new D8RegionData(
                    regionPos.x(), regionPos.z(),
                        featureRegionType,
                    d8Flow.crossings(),
                    d8Flow.primaryOutputDirection(), d8Flow.flowGrid(),
                    d8Flow.terminusCells(), d8Flow.riverPaths(),
                    d8Flow.confluenceCells()
                ));
            }
        }

        RiverTaleNetwork.sendToPlayer(new VisualizePacket(true, regionDataList), player);
    }

    public static RegionPos getPlayerRegion(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return RegionPos.at(pos.getX(), pos.getZ());
    }

    public static void clearEnabledPlayers() {
        ENABLED_PLAYERS.clear();
    }

    public static void removePlayer(UUID playerId) {
        ENABLED_PLAYERS.remove(playerId);
    }
}
