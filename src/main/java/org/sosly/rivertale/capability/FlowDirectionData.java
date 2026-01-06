package org.sosly.rivertale.capability;

import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import org.sosly.rivertale.core.FlowDirection;

public class FlowDirectionData {
    private final FlowDirection[][] directions = new FlowDirection[16][16];

    @Nullable
    public FlowDirection get(int x, int z) {
        if (x < 0 || x >= 16 || z < 0 || z >= 16) {
            return null;
        }
        return directions[x][z];
    }

    public void set(int x, int z, @Nullable FlowDirection direction) {
        if (x < 0 || x >= 16 || z < 0 || z >= 16) {
            return;
        }
        directions[x][z] = direction;
    }

    public CompoundTag serializeNBT() {
        CompoundTag tag = new CompoundTag();
        byte[] data = new byte[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                FlowDirection dir = directions[x][z];
                data[x * 16 + z] = dir == null ? -1 : (byte) dir.ordinal();
            }
        }
        tag.putByteArray("flow", data);
        return tag;
    }

    public void deserializeNBT(CompoundTag tag) {
        if (!tag.contains("flow")) {
            return;
        }
        byte[] data = tag.getByteArray("flow");
        if (data.length != 256) {
            return;
        }
        FlowDirection[] values = FlowDirection.values();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                byte b = data[x * 16 + z];
                directions[x][z] = (b < 0 || b >= values.length) ? null : values[b];
            }
        }
    }
}
