package org.sosly.rivertale.mixin;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraftforge.fml.loading.FMLLoader;
import org.sosly.rivertale.RiverTale;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    private static final ResourceKey<Biome> RIVER = ResourceKey.create(
        Registries.BIOME,
        new ResourceLocation("minecraft", "river")
    );
    private static final ResourceKey<Biome> FROZEN_RIVER = ResourceKey.create(
        Registries.BIOME,
        new ResourceLocation("minecraft", "frozen_river")
    );

    private Climate.ParameterList<Holder<Biome>> cachedFiltered = null;

    @Shadow
    protected abstract Climate.ParameterList<Holder<Biome>> parameters();

    @Inject(method = "parameters", at = @At("RETURN"), cancellable = true)
    private void filterParameterList(CallbackInfoReturnable<Climate.ParameterList<Holder<Biome>>> cir) {
        if (!FMLLoader.isProduction()) {
            return;
        }

        if (cachedFiltered != null) {
            cir.setReturnValue(cachedFiltered);
            return;
        }

        RiverTale.LOGGER.info("Removing river biomes from world generation...");

        AtomicBoolean progressRunning = new AtomicBoolean(true);
        Thread progressThread = new Thread(() -> {
            try {
                while (progressRunning.get()) {
                    Thread.sleep(5);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        progressThread.setDaemon(true);
        progressThread.start();

        try {
            Climate.ParameterList<Holder<Biome>> original = cir.getReturnValue();
            cachedFiltered = new Climate.ParameterList<>(
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
            cir.setReturnValue(cachedFiltered);
        } finally {
            progressRunning.set(false);
            try {
                progressThread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println();
            RiverTale.LOGGER.info("River biomes removed successfully");
        }
    }
}
