package org.sosly.rivertale.density;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.config.CommonConfig;

public record Sample(
        ChunkPos pos,
        double continents,
        double depth,
        double erosion,
        double ridges,
        double temperature,
        double vegetation
) {
    public boolean isOcean() {
        return continents < CommonConfig.get().oceanThreshold();
    }

    public double ridgesFolded() {
        return -3.0 * (Math.abs(Math.abs(-ridges) - 0.6666666) - 0.3333333);
    }

    public int estimatedHeight() {
        return (int) Math.round(70 + 144 * depth);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.toLong());
        tag.putDouble("continents", continents);
        tag.putDouble("depth", depth);
        tag.putDouble("erosion", erosion);
        tag.putDouble("ridges", ridges);
        tag.putDouble("temperature", temperature);
        tag.putDouble("vegetation", vegetation);
        return tag;
    }

    public static Sample decode(CompoundTag tag) {
        return new Sample(
            new ChunkPos(tag.getLong("pos")),
            tag.getDouble("continents"),
            tag.getDouble("depth"),
            tag.getDouble("erosion"),
            tag.getDouble("ridges"),
            tag.getDouble("temperature"),
            tag.getDouble("vegetation")
        );
    }
}
