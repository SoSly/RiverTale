package org.sosly.rivertale.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.RiverTale;

@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID, value = Dist.CLIENT)
public class EventHandler {

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Cache.setEnabled(false);
    }
}
