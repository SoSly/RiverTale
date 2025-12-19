package org.sosly.rivertale;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.network.RiverTaleNetwork;

@Mod(RiverTale.MOD_ID)
public class RiverTale {
    public static final String MOD_ID = "rivertale";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RiverTale() {
        LOGGER.info("Loading RiverTale");

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RiverConfig.SPEC);

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::onLoadComplete);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("RiverTale common setup");
            RiverTaleNetwork.register();
        });
    }

    private void onLoadComplete(final FMLLoadCompleteEvent event) {
        LOGGER.info("RiverTale loaded");
    }
}
