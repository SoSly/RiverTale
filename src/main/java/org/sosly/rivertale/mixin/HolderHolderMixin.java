package org.sosly.rivertale.mixin;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.sosly.rivertale.world.RiverSuppression;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DensityFunctions.HolderHolder.class)
public class HolderHolderMixin {
    @Inject(method = "mapAll", at = @At("HEAD"), cancellable = true)
    private void interceptDensityFunction(DensityFunction.Visitor visitor, CallbackInfoReturnable<DensityFunction> cir) {
        DensityFunction result = RiverSuppression.intercept((DensityFunctions.HolderHolder)(Object)this, visitor);
        if (result != null) {
            cir.setReturnValue(result);
        }
    }
}
