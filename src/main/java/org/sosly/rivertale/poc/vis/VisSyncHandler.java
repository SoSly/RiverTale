package org.sosly.rivertale.poc.vis;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber
public class VisSyncHandler {

    private static final Map<UUID, Long> LAST_KNOWN_REGION = new HashMap<>();
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

            if (!VisCommand.isEnabled(playerId)) {
                continue;
            }

            long currentRegion = VisCommand.getPlayerRegionKey(player);
            Long lastRegion = LAST_KNOWN_REGION.get(playerId);

            if (lastRegion == null || lastRegion != currentRegion) {
                LAST_KNOWN_REGION.put(playerId, currentRegion);
                VisCommand.sendDataToPlayer(player);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        LAST_KNOWN_REGION.clear();
        VisCommand.disableAll();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId = event.getEntity().getUUID();
        LAST_KNOWN_REGION.remove(playerId);
        VisCommand.disable(playerId);
    }
}
