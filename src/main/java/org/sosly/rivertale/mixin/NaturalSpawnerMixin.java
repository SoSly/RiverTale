package org.sosly.rivertale.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    @Inject(
        method = "spawnForChunk",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void skipMobSpawnsInDevMode(
        ServerLevel level,
        LevelChunk chunk,
        NaturalSpawner.SpawnState spawnState,
        boolean spawnFriendlies,
        boolean spawnEnemies,
        boolean forcedSpawn,
        CallbackInfo ci
    ) {
        if (!FMLLoader.isProduction()) {
            ci.cancel();
        }
    }
}
