package org.sosly.rivertale.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
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
        Boat self = (Boat) (Object) this;
        AABB aabb = self.getBoundingBox();
        double boatTop = aabb.maxY + 0.001D;

        int minX = Mth.floor(aabb.minX);
        int maxX = Mth.ceil(aabb.maxX);
        int minZ = Mth.floor(aabb.minZ);
        int maxZ = Mth.ceil(aabb.maxZ);

        int boatTopBlock = Mth.floor(boatTop);
        int searchCeiling = boatTopBlock + 3;

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int y = boatTopBlock; y <= searchCeiling; y++) {
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    pos.set(x, y, z);
                    FluidState fluid = self.level().getFluidState(pos);

                    if (!fluid.is(FluidTags.WATER)) {
                        continue;
                    }
                    if (!fluid.isSource()) {
                        continue;
                    }
                    if (aabb.minY >= pos.getY()) {
                        continue;
                    }

                    cir.setReturnValue(Boat.Status.UNDER_WATER);
                    return;
                }
            }
        }

        cir.setReturnValue(null);
    }

    @Inject(method = "checkInWater", at = @At("HEAD"), cancellable = true)
    private void checkWaterBelow(CallbackInfoReturnable<Boolean> cir) {
        Boat self = (Boat) (Object) this;
        AABB aabb = self.getBoundingBox();

        int minX = Mth.floor(aabb.minX);
        int maxX = Mth.ceil(aabb.maxX);
        int minZ = Mth.floor(aabb.minZ);
        int maxZ = Mth.ceil(aabb.maxZ);

        int boatBottomBlock = Mth.floor(aabb.minY);

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double highestWater = -Double.MAX_VALUE;
        boolean inWater = false;

        for (int y = boatBottomBlock; y <= boatBottomBlock + 1; y++) {
            for (int x = minX; x < maxX; x++) {
                for (int z = minZ; z < maxZ; z++) {
                    pos.set(x, y, z);
                    FluidState fluid = self.level().getFluidState(pos);

                    if (!fluid.is(FluidTags.WATER)) {
                        continue;
                    }

                    float waterSurface = y + fluid.getHeight(self.level(), pos);
                    if (waterSurface > highestWater) {
                        highestWater = waterSurface;
                    }
                    if (aabb.minY < waterSurface) {
                        inWater = true;
                    }
                }
            }
        }

        if (highestWater > -Double.MAX_VALUE) {
            this.waterLevel = highestWater;
            cir.setReturnValue(true);
            return;
        }

        if (inWater) {
            cir.setReturnValue(true);
        }
    }
}
