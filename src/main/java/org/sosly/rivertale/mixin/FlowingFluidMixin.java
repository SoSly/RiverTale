package org.sosly.rivertale.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.physics.FluidFlow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Inject(method = "getFlow", at = @At("HEAD"), cancellable = true)
    private void overrideRiverFlow(
            BlockGetter level,
            BlockPos pos,
            FluidState fluidState,
            CallbackInfoReturnable<Vec3> cir) {

        Vec3 flowVec = FluidFlow.getOverrideFlow(level, pos);
        if (flowVec != null) {
            cir.setReturnValue(flowVec);
        }
    }
}
