package org.sosly.rivertale.mixin;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import org.sosly.rivertale.RiverTale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Aquifer.NoiseBasedAquifer.class)
public class NoiseBasedAquiferMixin {

    @Unique
    private DensityFunction rivertaleContinents;

    private static final int SEA_LEVEL = 63;
    private static boolean loggedOnce = false;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void captureContinent(NoiseChunk noiseChunk, ChunkPos chunkPos, NoiseRouter noiseRouter,
                                  PositionalRandomFactory positionalRandomFactory, int minY, int height,
                                  Aquifer.FluidPicker globalFluidPicker, CallbackInfo ci) {
        // Capture the continents function from the noise router
        this.rivertaleContinents = noiseRouter.continents();
    }

    /**
     * Prevents aquifers from placing water inland at or below sea level.
     * Only affects areas where continentalness > 0 and Y <= 63.
     */
    @Inject(method = "computeSubstance", at = @At("HEAD"), cancellable = true)
    private void preventWaterInValleys(DensityFunction.FunctionContext context, double substance,
                                       CallbackInfoReturnable<BlockState> cir) {
        // If substance is positive, it's solid terrain - don't interfere
        if (substance > 0.0D) {
            return;
        }

        int y = context.blockY();

        // Only prevent water at or below sea level
        if (y > SEA_LEVEL) {
            return;
        }

        // Check continentalness to determine if we're inland
        double continentalness = rivertaleContinents.compute(context);

        // Positive continentalness means we're inland
        // Values typically range from about -1.0 (deep ocean) to 1.0 (far inland)
        // 0 is roughly the coastline
        if (continentalness > 0) {
            if (!loggedOnce) {
                RiverTale.LOGGER.info("RiverTale: Preventing inland water at Y={}, continentalness={}",
                                     y, continentalness);
                loggedOnce = true;
            }

            // Return null means "no fluid here, leave as air"
            cir.setReturnValue(null);
        }
    }
}
