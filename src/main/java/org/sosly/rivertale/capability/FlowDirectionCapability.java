package org.sosly.rivertale.capability;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.core.FlowDirection;

public class FlowDirectionCapability {
    public static final Capability<FlowDirectionData> CAPABILITY =
        CapabilityManager.get(new CapabilityToken<>() {});

    public static final ResourceLocation ID = new ResourceLocation(RiverTale.MOD_ID, "flow_direction");

    private static final Map<ChunkPos, FlowDirection[][]> PENDING = new ConcurrentHashMap<>();

    public static void register(RegisterCapabilitiesEvent event) {
        event.register(FlowDirectionData.class);
    }

    @Nullable
    public static FlowDirectionData get(LevelChunk chunk) {
        return chunk.getCapability(CAPABILITY).orElse(null);
    }

    public static void storePending(ChunkPos pos, FlowDirection[][] directions) {
        PENDING.put(pos, directions);
    }

    @Nullable
    public static FlowDirection[][] getPending(ChunkPos pos) {
        return PENDING.get(pos);
    }

    public static void clearPending(ChunkPos pos) {
        PENDING.remove(pos);
    }

    public static class Provider implements ICapabilitySerializable<CompoundTag> {
        private final FlowDirectionData data = new FlowDirectionData();
        private final LazyOptional<FlowDirectionData> optional = LazyOptional.of(() -> data);
        private final ChunkPos chunkPos;

        public Provider(ChunkPos chunkPos) {
            this.chunkPos = chunkPos;
            FlowDirection[][] pending = PENDING.get(chunkPos);
            if (pending != null) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        data.set(x, z, pending[x][z]);
                    }
                }
            }
        }

        @Nonnull
        @Override
        public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable net.minecraft.core.Direction side) {
            if (cap == CAPABILITY) {
                return optional.cast();
            }
            return LazyOptional.empty();
        }

        @Override
        public CompoundTag serializeNBT() {
            return data.serializeNBT();
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            data.deserializeNBT(nbt);
        }

        public void invalidate() {
            optional.invalidate();
        }
    }
}
