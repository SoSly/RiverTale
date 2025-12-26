package org.sosly.rivertale.mixin;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.region.RegionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(method = "doFill", at = @At("TAIL"))
    private void onChunkFill(
            Blender blender,
            StructureManager structureManager,
            RandomState randomState,
            ChunkAccess chunk,
            int minCellY,
            int cellCountY,
            CallbackInfoReturnable<ChunkAccess> cir) {

        RegionPos center = new RegionPos(chunk.getPos().getMiddleBlockPosition(0));
        SampleCache sampleCache = SampleCache.get();
        RegionType.classify(center, sampleCache);

        for (Direction dir : Direction.D8) {
            RegionType.classify(center.relative(dir), sampleCache);
        }
    }
}
