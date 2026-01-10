package org.sosly.rivertale.mixin;

import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.world.RiverShaping;
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

        Timer.Record timer = Store.getTimer(ChunkGeneratorMixin.class, "onChunkFill").start();
        RiverShaping.generateRiverMap(chunk);
        timer.stop();
    }
}
