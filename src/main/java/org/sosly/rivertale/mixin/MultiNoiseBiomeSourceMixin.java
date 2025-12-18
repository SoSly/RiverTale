package org.sosly.rivertale.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.sosly.rivertale.RiverTale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Collectors;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    @Unique
    private static final ResourceKey<Biome> RIVER = ResourceKey.create(
        Registries.BIOME,
        new ResourceLocation("minecraft", "river")
    );

    @Unique
    private static final ResourceKey<Biome> FROZEN_RIVER = ResourceKey.create(
        Registries.BIOME,
        new ResourceLocation("minecraft", "frozen_river")
    );

    @Unique
    private Climate.ParameterList<Holder<Biome>> rivertale$cachedFiltered = null;

    @Inject(method = "parameters", at = @At("RETURN"), cancellable = true)
    private void rivertale$filterParameterList(CallbackInfoReturnable<Climate.ParameterList<Holder<Biome>>> cir) {
        if (rivertale$cachedFiltered != null) {
            cir.setReturnValue(rivertale$cachedFiltered);
            return;
        }

        Climate.ParameterList<Holder<Biome>> original = cir.getReturnValue();
        rivertale$cachedFiltered = new Climate.ParameterList<>(
            original.values().stream()
                .filter(pair -> {
                    Holder<Biome> biome = pair.getSecond();
                    if (!biome.unwrapKey().isPresent()) {
                        return true;
                    }
                    ResourceKey<Biome> key = biome.unwrapKey().get();
                    return !key.equals(RIVER) && !key.equals(FROZEN_RIVER);
                })
                .collect(Collectors.toList())
        );
        cir.setReturnValue(rivertale$cachedFiltered);
    }
}
