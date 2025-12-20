package org.sosly.rivertale.mixin;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.sosly.rivertale.worldgen.river.CellDensityProvider;
import org.sosly.rivertale.worldgen.river.RiverCellKey;
import org.sosly.rivertale.worldgen.river.RiverCellManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(method = "doFill", at = @At("TAIL"))
    private void rivertaleComputeCellOnNoise(
            Blender blender,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk,
            int minCellY,
            int cellCountY,
            CallbackInfoReturnable<ChunkAccess> cir) {

        CellDensityProvider provider = new CellDensityProvider(randomState);
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        RiverCellKey minKey = RiverCellKey.fromBlockPos(minX, minZ);
        RiverCellKey maxKey = RiverCellKey.fromBlockPos(maxX, maxZ);

        for (int cellX = minKey.cellX(); cellX <= maxKey.cellX(); cellX++) {
            for (int cellZ = minKey.cellZ(); cellZ <= maxKey.cellZ(); cellZ++) {
                RiverCellKey cellKey = new RiverCellKey(cellX, cellZ);
                RiverCellManager.getOrCreate(cellKey, provider, randomState);
            }
        }
    }
}
