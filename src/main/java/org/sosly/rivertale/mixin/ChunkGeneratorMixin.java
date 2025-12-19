package org.sosly.rivertale.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.worldgen.river.ContinentsDensityProvider;
import org.sosly.rivertale.worldgen.river.RiverCellKey;
import org.sosly.rivertale.worldgen.river.RiverCellManager;
import org.sosly.rivertale.worldgen.river.RiverCellSavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void rivertaleComputeCellOnSurface(
            WorldGenRegion region,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk,
            CallbackInfo ci) {

        ServerLevel level = region.getLevel();
        ChunkPos chunkPos = chunk.getPos();
        int minX = chunkPos.getMinBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxX = chunkPos.getMaxBlockX();
        int maxZ = chunkPos.getMaxBlockZ();

        ContinentsDensityProvider provider = new ContinentsDensityProvider(level);
        long worldSeed = level.getSeed();
        RiverCellSavedData savedData = RiverCellSavedData.get(level);

        RiverCellKey minKey = RiverCellKey.fromBlockPos(minX, minZ);
        RiverCellKey maxKey = RiverCellKey.fromBlockPos(maxX, maxZ);

        for (int cellX = minKey.cellX(); cellX <= maxKey.cellX(); cellX++) {
            for (int cellZ = minKey.cellZ(); cellZ <= maxKey.cellZ(); cellZ++) {
                RiverCellKey cellKey = new RiverCellKey(cellX, cellZ);
                RiverCellManager.getOrCreate(cellKey, provider, worldSeed, savedData);
            }
        }
    }
}
