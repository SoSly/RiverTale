package org.sosly.rivertale.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.river.WatershedCache;
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

        CellCache cellCache = CellCache.get();
        if (cellCache == null) {
            return;
        }

        CellPos cellPos = new CellPos(pos);
        Cell cell = cellCache.getIfPresent(cellPos);
        if (cell == null) {
            return;
        }

        Feature feature = cell.feature();
        if (feature == Feature.DEFAULT || feature == Feature.DIVIDE) {
            return;
        }

        WatershedCache watershedCache = WatershedCache.get();
        if (watershedCache == null) {
            return;
        }

        Watershed watershed = watershedCache.getWatershed(cellPos);
        if (watershed == null) {
            return;
        }

        CellPos downstream = watershed.downstream(cellPos);
        if (downstream == null) {
            return;
        }

        int dx = downstream.x() - cellPos.x();
        int dz = downstream.z() - cellPos.z();

        Vec3 flowVec = new Vec3(dx, 0.0, dz).normalize();
        cir.setReturnValue(flowVec);
    }
}
