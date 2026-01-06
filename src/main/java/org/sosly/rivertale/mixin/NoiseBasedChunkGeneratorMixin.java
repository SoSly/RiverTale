package org.sosly.rivertale.mixin;

import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraftforge.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseBasedChunkGenerator.class)
public abstract class NoiseBasedChunkGeneratorMixin {

    @Inject(
        method = "spawnOriginalMobs",
        at = @At("HEAD"),
        cancellable = true
    )
    private void skipInitialMobSpawnsInDevMode(WorldGenRegion level, CallbackInfo ci) {
        if (!FMLLoader.isProduction()) {
            ci.cancel();
        }
    }
}
