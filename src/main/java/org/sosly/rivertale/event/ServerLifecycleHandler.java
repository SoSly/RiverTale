package org.sosly.rivertale.event;

import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.level.ChunkWatchEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.capability.FlowDirectionCapability;
import org.sosly.rivertale.capability.FlowDirectionData;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.density.NoiseBasedSampleProvider;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.networking.FlowDirectionSyncPacket;
import org.sosly.rivertale.networking.Network;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionTypeCache;
import org.sosly.rivertale.river.WatershedCache;
import org.sosly.rivertale.world.RiverBuilder;
import org.sosly.rivertale.world.WorldSettings;

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

        ChunkSource chunkSource = serverLevel.getChunkSource();
        if (!(chunkSource instanceof ServerChunkCache serverChunkCache)) {
            return;
        }

        if (!(serverChunkCache.getGenerator() instanceof NoiseBasedChunkGenerator generator)) {
            return;
        }

        NoiseGeneratorSettings settings = generator.generatorSettings().value();
        WorldSettings.init(settings.seaLevel());

        RandomState randomState = RandomState.create(
            settings,
            serverLevel.registryAccess().lookupOrThrow(Registries.NOISE),
            serverLevel.getSeed()
        );

        SampleCache.init(NoiseBasedSampleProvider.create(randomState, WorldSettings.get().seaLevel()));
        CellCache.init();
        RegionTypeCache.init();
        RegionCache.init();
        WatershedCache.init();
        RiverBuilder.init(serverLevel.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.RIVER));
        RiverTale.LOGGER.info("Caches initialized on level load");
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        RiverBuilder.shutdown();
        WatershedCache.shutdown();
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
        WorldSettings.shutdown();
        RiverTale.LOGGER.info("Caches cleared");
    }

    @SubscribeEvent
    public void onChunkWatch(ChunkWatchEvent.Watch event) {
        ServerPlayer player = event.getPlayer();
        LevelChunk chunk = event.getLevel().getChunk(event.getPos().x, event.getPos().z);
        ChunkPos chunkPos = chunk.getPos();

        Direction[][] directions = null;

        FlowDirectionData flowData = FlowDirectionCapability.get(chunk);
        if (flowData != null) {
            directions = new Direction[16][16];
            boolean hasData = false;
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    Direction dir = flowData.get(x, z);
                    if (dir != null) {
                        directions[x][z] = dir;
                        hasData = true;
                    }
                }
            }
            if (!hasData) {
                directions = null;
            }
        }

        if (directions == null) {
            directions = FlowDirectionCapability.getPending(chunkPos);
        }

        if (directions != null) {
            Network.sendToPlayer(new FlowDirectionSyncPacket(chunkPos, directions), player);
        }
    }
}
