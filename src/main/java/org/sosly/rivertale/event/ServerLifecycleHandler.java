package org.sosly.rivertale.event;

import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.command.LocateCommand;
import org.sosly.rivertale.terrain.RegionDensityProvider;

@Mod.EventBusSubscriber
public class ServerLifecycleHandler {

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        RegionDensityProvider.clearCaches();
        LocateCommand.shutdown();
    }
}
