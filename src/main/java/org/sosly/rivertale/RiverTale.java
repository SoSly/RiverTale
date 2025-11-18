package org.sosly.rivertale;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(RiverTale.MOD_ID)
public class RiverTale {
    public static final String MOD_ID = "rivertale";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RiverTale() {
        LOGGER.info("Loading RiverTale");

        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        modEventBus.addListener(this::setup);
        modEventBus.addListener(this::onLoadComplete);
    }

    private void setup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LOGGER.info("RiverTale common setup");
        });
    }

    private void onLoadComplete(final FMLLoadCompleteEvent event) {
        LOGGER.info("RiverTale loaded");
    }
}
