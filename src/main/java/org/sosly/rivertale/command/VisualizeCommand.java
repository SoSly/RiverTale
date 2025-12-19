package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.sosly.rivertale.network.RiverTaleNetwork;
import org.sosly.rivertale.network.VisualizeCellsPacket;
import org.sosly.rivertale.worldgen.river.ContinentsDensityProvider;
import org.sosly.rivertale.worldgen.river.RiverCell;
import org.sosly.rivertale.worldgen.river.RiverCellKey;
import org.sosly.rivertale.worldgen.river.RiverCellManager;
import org.sosly.rivertale.worldgen.river.RiverCellSavedData;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
                    RiverTaleNetwork.sendToPlayer(new VisualizeCellsPacket(false, new ArrayList<>()), player);
                    source.sendSuccess(() -> Component.literal("River visualization disabled")
                        .withStyle(ChatFormatting.YELLOW), false);
                    return 1;
                }

                ENABLED_PLAYERS.add(playerId);
                sendCellDataToPlayer(player);
                source.sendSuccess(() -> Component.literal("River visualization enabled")
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
        long worldSeed = level.getSeed();

        RiverCellSavedData savedData = RiverCellSavedData.get(level);
        ContinentsDensityProvider provider = new ContinentsDensityProvider(level);

        RiverCellKey playerCell = RiverCellKey.fromBlockPos(pos.getX(), pos.getZ());
        List<VisualizeCellsPacket.CellData> cellDataList = new ArrayList<>();

        for (int dx = -CELL_RADIUS; dx <= CELL_RADIUS; dx++) {
            for (int dz = -CELL_RADIUS; dz <= CELL_RADIUS; dz++) {
                RiverCellKey key = new RiverCellKey(playerCell.cellX() + dx, playerCell.cellZ() + dz);
                RiverCell cell = RiverCellManager.getOrCreate(key, provider, worldSeed, savedData);
                RiverCellManager.ensurePaths(cell, provider, worldSeed, savedData);

                if (!cell.getRiverPaths().isEmpty()) {
                    cellDataList.add(new VisualizeCellsPacket.CellData(
                        key.cellX(),
                        key.cellZ(),
                        cell.getClassification(),
                        cell.isBasin(),
                        cell.getRiverPaths()
                    ));
                }
            }
        }

        RiverTaleNetwork.sendToPlayer(new VisualizeCellsPacket(true, cellDataList), player);
    }

    public static RiverCellKey getPlayerCell(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        return RiverCellKey.fromBlockPos(pos.getX(), pos.getZ());
    }
}
