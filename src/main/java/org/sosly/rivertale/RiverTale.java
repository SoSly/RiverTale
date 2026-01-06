package org.sosly.rivertale;

import com.mojang.logging.LogUtils;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.sosly.rivertale.capability.FlowDirectionCapability;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.event.FluidHandler;
import org.sosly.rivertale.event.ServerLifecycleHandler;
import org.sosly.rivertale.networking.Network;
import org.sosly.rivertale.world.RiverSuppression;

@Mod(RiverTale.MOD_ID)
public class RiverTale {
    public static final String MOD_ID = "rivertale";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RiverTale() {
        LOGGER.info("Loading RiverTale");

        RiverSuppression.init();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CommonConfig.Spec.SPEC);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::onLoadComplete);
        modEventBus.addListener(FlowDirectionCapability::register);

        MinecraftForge.EVENT_BUS.register(new FluidHandler());
        MinecraftForge.EVENT_BUS.register(new ServerLifecycleHandler());
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            CommonConfig.Spec.load();
            Network.register();
        });
    }

    private void onLoadComplete(final FMLLoadCompleteEvent event) {
        LOGGER.info("RiverTale loaded");
    }
}
