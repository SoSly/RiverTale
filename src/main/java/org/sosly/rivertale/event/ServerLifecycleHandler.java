package org.sosly.rivertale.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.RtfSampleProvider;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionTypeCache;
import org.sosly.rivertale.world.RiverShaping;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.RTFRandomState;

public class ServerLifecycleHandler {
    private final Map<UUID, RegionPos> playerRegions = new HashMap<>();

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

        RiverShaping.init(context.levels.waterLevel, randomState, BiomeManager.obfuscateSeed(serverLevel.getSeed()));
        RiverTale.LOGGER.info("Rivertale initialized with RTF base terrain samples and sea level {}", context.levels.waterLevel);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            preparePlayerRegion(player);
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) {
            preparePlayerRegion(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        playerRegions.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            preparePlayerRegion(player);
        }
    }

    private void preparePlayerRegion(ServerPlayer player) {
        UUID id = player.getUUID();
        ServerLevel level = player.serverLevel();
        RandomState state = level.getChunkSource().randomState();
        if (!level.dimension().equals(Level.OVERWORLD) || !RiverShaping.isActive(state)) {
            playerRegions.remove(id);
            return;
        }

        RegionPos current = new RegionPos(player.blockPosition());
        RegionPos previous = playerRegions.put(id, current);
        if (!current.equals(previous)) {
            RiverShaping.prepareNearby(current, state);
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        playerRegions.clear();
        RiverShaping.shutdown();
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
        RiverTale.LOGGER.info("Caches cleared");
    }
}
