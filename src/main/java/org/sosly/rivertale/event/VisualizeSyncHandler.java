package org.sosly.rivertale.event;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.command.VisualizeCommand;
import org.sosly.rivertale.worldgen.river.RiverRegionKey;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class VisualizeSyncHandler {

    private static final Map<UUID, RiverRegionKey> LAST_KNOWN_REGION = new HashMap<>();
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

            RiverRegionKey currentRegion = VisualizeCommand.getPlayerRegion(player);
            RiverRegionKey lastRegion = LAST_KNOWN_REGION.get(playerId);

            if (lastRegion == null || !lastRegion.equals(currentRegion)) {
                LAST_KNOWN_REGION.put(playerId, currentRegion);
                VisualizeCommand.sendRegionDataToPlayer(player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LAST_KNOWN_REGION.clear();
        VisualizeCommand.clearEnabledPlayers();
    }
}
