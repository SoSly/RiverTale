package org.sosly.rivertale.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.worldgen.ValleyFillingDensityFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(NoiseChunk.class)
public class NoiseChunkMixin {

    @Unique
    private static final ThreadLocal<RandomState> CAPTURED_RANDOM_STATE = new ThreadLocal<>();

    @Unique
    private static boolean loggedOnce = false;

    @ModifyVariable(
        method = "<init>",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private static RandomState captureRandomState(RandomState pRandomState) {
        CAPTURED_RANDOM_STATE.set(pRandomState);
        return pRandomState;
    }

    @Redirect(
        method = "<init>",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/level/levelgen/DensityFunctions;add(Lnet/minecraft/world/level/levelgen/DensityFunction;Lnet/minecraft/world/level/levelgen/DensityFunction;)Lnet/minecraft/world/level/levelgen/DensityFunction;",
            ordinal = 0
        )
    )
    private DensityFunction wrapFinalDensity(DensityFunction finalDensity, DensityFunction beardifier) {
        RandomState randomState = CAPTURED_RANDOM_STATE.get();
        if (randomState == null) {
            RiverTale.LOGGER.warn("RiverTale: No RandomState captured - valley filling disabled");
            return DensityFunctions.add(finalDensity, beardifier);
        }

        try {
            NoiseRouter router = randomState.router();
            DensityFunction continents = router.continents();
            DensityFunction ridges = router.ridges();

            if (!loggedOnce) {
                RiverTale.LOGGER.info("RiverTale: Valley filling enabled with NoiseRouter access");
                loggedOnce = true;
            }

            DensityFunction wrapped = new ValleyFillingDensityFunction(
                finalDensity,
                continents,
                ridges
            );

            return DensityFunctions.add(wrapped, beardifier);
        } finally {
            CAPTURED_RANDOM_STATE.remove();
        }
    }
}
