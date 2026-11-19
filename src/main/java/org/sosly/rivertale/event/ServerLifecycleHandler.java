package org.sosly.rivertale.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.density.RtfSampleProvider;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionTypeCache;
import org.sosly.rivertale.river.WatershedCache;
import org.sosly.rivertale.world.RiverShaping;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;

public class ServerLifecycleHandler {

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (SampleCache.isInitialized()) {
            return;
        }

        LevelAccessor level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!serverLevel.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        RandomState randomState = serverLevel.getChunkSource().randomState();
        if (!((Object) randomState instanceof RTFRandomState rtfState)) {
            RiverTale.LOGGER.warn("Rivertale requires an RTF overworld preset; terrain classification is inactive.");
            return;
        }

        GeneratorContext context = rtfState.generatorContext();
        if (context == null) {
            RiverTale.LOGGER.info("This overworld has no RTF terrain context; Rivertale classification is inactive.");
            return;
        }

        SampleCache.init(new RtfSampleProvider(context));
        CellCache.init();
        RegionTypeCache.init();
        RegionCache.init();
        WatershedCache.init();

        RiverShaping.init(context.levels.waterLevel, randomState);
        RiverTale.LOGGER.info("Rivertale initialized with RTF base terrain samples and sea level {}", context.levels.waterLevel);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        RiverShaping.shutdown();
        WatershedCache.shutdown();
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
        RiverTale.LOGGER.info("Caches cleared");
    }
}
