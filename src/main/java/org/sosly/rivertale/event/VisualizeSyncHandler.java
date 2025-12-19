package org.sosly.rivertale.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.command.VisualizeCommand;
import org.sosly.rivertale.worldgen.river.RiverCellKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class VisualizeSyncHandler {

    private static final Map<UUID, RiverCellKey> LAST_KNOWN_CELL = new HashMap<>();
    private static int tickCounter = 0;
    private static final int SYNC_INTERVAL = 20;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickCounter++;
        if (tickCounter < SYNC_INTERVAL) {
            return;
        }

        tickCounter = 0;

        if (event.getServer() == null) {
            return;
        }

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            UUID playerId = player.getUUID();

            if (!VisualizeCommand.isVisualizationEnabled(playerId)) {
                continue;
            }

            RiverCellKey currentCell = VisualizeCommand.getPlayerCell(player);
            RiverCellKey lastCell = LAST_KNOWN_CELL.get(playerId);

            if (lastCell == null || !lastCell.equals(currentCell)) {
                LAST_KNOWN_CELL.put(playerId, currentCell);
                VisualizeCommand.sendCellDataToPlayer(player);
            }
        }
    }
}
