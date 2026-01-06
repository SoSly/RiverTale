package org.sosly.rivertale.mixin;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.sosly.rivertale.world.RiverSuppression;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    @Unique
    private Climate.ParameterList<Holder<Biome>> cachedFiltered = null;

    @Inject(method = "parameters", at = @At("RETURN"), cancellable = true)
    private void filterRiverBiomes(CallbackInfoReturnable<Climate.ParameterList<Holder<Biome>>> cir) {
        if (cachedFiltered != null) {
            cir.setReturnValue(cachedFiltered);
            return;
        }

        cachedFiltered = RiverSuppression.filterRiverBiomes(cir.getReturnValue());
        cir.setReturnValue(cachedFiltered);
    }
}
