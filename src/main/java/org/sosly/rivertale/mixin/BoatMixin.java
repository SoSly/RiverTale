package org.sosly.rivertale.mixin;

import net.minecraft.world.entity.vehicle.Boat;
import org.sosly.rivertale.physics.BoatPhysics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Boat.class)
public abstract class BoatMixin {

    @Shadow
    private double waterLevel;

    @Inject(method = "isUnderwater", at = @At("HEAD"), cancellable = true)
    private void fixGradientSinking(CallbackInfoReturnable<Boat.Status> cir) {
        Boat.Status result = BoatPhysics.checkUnderwater((Boat) (Object) this);
        if (result != null) {
            cir.setReturnValue(result);
            return;
        }
        cir.setReturnValue(null);
    }

    @Inject(method = "checkInWater", at = @At("HEAD"), cancellable = true)
    private void checkWaterBelow(CallbackInfoReturnable<Boolean> cir) {
        BoatPhysics.WaterCheckResult result = BoatPhysics.checkInWater((Boat) (Object) this);
        if (result == null) {
            return;
        }

        if (result.waterLevel() > -Double.MAX_VALUE) {
            this.waterLevel = result.waterLevel();
        }
        cir.setReturnValue(result.inWater());
    }
}
