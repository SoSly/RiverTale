package org.sosly.rivertale.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.data.RegionKey;
import org.sosly.rivertale.worldgen.cache.ContinentCacheManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(ServerChunkCache.class)
public class ServerChunkCacheMixin {
    @Unique
    private final Set<RegionKey> rivertaleProcessedRegions = ConcurrentHashMap.newKeySet();

    @Shadow
    public ServerLevel level;

    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"))
    private void ensureContinentCached(int x, int z, ChunkStatus status, boolean load,
                                        CallbackInfoReturnable<ChunkAccess> cir) {
        if (status != ChunkStatus.FULL) {
            return;
        }
        if (!this.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        BlockPos chunkCenter = new ChunkPos(x, z).getWorldPosition();
        RegionKey region = RegionKey.fromBlockPos(chunkCenter);

        if (!rivertaleProcessedRegions.add(region)) {
            return;
        }

        ContinentCacheManager manager = ContinentCacheManager.forLevel(this.level);
        if (manager == null) {
            rivertaleProcessedRegions.remove(region);
            return;
        }

        RandomState randomState = this.level.getChunkSource().randomState();
        NoiseRouter router = randomState.router();

        RiverTale.LOGGER.info("RiverTale: Triggering continent detection for region ({}, {})",
            region.getRegionX(), region.getRegionZ());
        manager.getContinentalData(chunkCenter, router.continents(), router.depth());
    }
}
