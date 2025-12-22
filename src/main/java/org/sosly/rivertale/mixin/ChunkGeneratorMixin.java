package org.sosly.rivertale.mixin;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.sosly.rivertale.worldgen.river.RegionDensityProvider;
import org.sosly.rivertale.worldgen.river.RiverRegionKey;
import org.sosly.rivertale.worldgen.river.RiverRegionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(method = "doFill", at = @At("TAIL"))
    private void rivertaleComputeRegionOnNoise(
            Blender blender,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk,
            int minRegionY,
            int regionCountY,
            CallbackInfoReturnable<ChunkAccess> cir) {

        RegionDensityProvider provider = new RegionDensityProvider(randomState);
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        RiverRegionKey minKey = RiverRegionKey.fromBlockPos(minX, minZ);
        RiverRegionKey maxKey = RiverRegionKey.fromBlockPos(maxX, maxZ);

        for (int regionX = minKey.regionX(); regionX <= maxKey.regionX(); regionX++) {
            for (int regionZ = minKey.regionZ(); regionZ <= maxKey.regionZ(); regionZ++) {
                RiverRegionKey regionKey = new RiverRegionKey(regionX, regionZ);
                RiverRegionManager.createRegionFor(regionKey, provider, randomState);
            }
        }
    }
}
