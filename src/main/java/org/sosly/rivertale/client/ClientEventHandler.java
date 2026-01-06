package org.sosly.rivertale.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.sosly.rivertale.RiverTale;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientRegionCache.setEnabled(false);
        FlowDirectionClientCache.clear();
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        FlowDirectionClientCache.remove(event.getChunk().getPos());
    }

    @Mod.EventBusSubscriber(modid = RiverTale.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            ClientRegionCache.init();
        }
    }
}
