package org.sosly.rivertale.mixin;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraftforge.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {

    @Inject(
        method = "save(Lnet/minecraft/world/level/chunk/ChunkAccess;)Z",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ChunkMap;write(Lnet/minecraft/world/level/ChunkPos;Lnet/minecraft/nbt/CompoundTag;)V"
        ),
        cancellable = true
    )
    private void skipDiskWrite(ChunkAccess chunk, CallbackInfoReturnable<Boolean> cir) {
        if (!FMLLoader.isProduction()) {
            cir.setReturnValue(true);
        }
    }
}
