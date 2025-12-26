package org.sosly.rivertale.event;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.density.NoiseBasedSampleProvider;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.region.RegionCache;

public class ServerLifecycleHandler {
    private static final int SAMPLE_Y = 63;

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (SampleCache.isInitialized()) {
            return;
        }

        LevelAccessor level = event.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        ChunkSource chunkSource = serverLevel.getChunkSource();
        if (!(chunkSource instanceof ServerChunkCache serverChunkCache)) {
            return;
        }

        if (!(serverChunkCache.getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            return;
        }

        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        RandomState randomState = RandomState.create(
            settings,
            serverLevel.registryAccess().lookupOrThrow(Registries.NOISE),
            serverLevel.getSeed()
        );

        SampleCache.init(NoiseBasedSampleProvider.create(randomState, SAMPLE_Y));
        CellCache.init();
        RegionCache.init();
        RiverTale.LOGGER.info("Caches initialized on level load");
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        RegionCache.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
        RiverTale.LOGGER.info("Caches cleared");
    }
}
