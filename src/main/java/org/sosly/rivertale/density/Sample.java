package org.sosly.rivertale.density;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.config.CommonConfig;

public record Sample(
        ChunkPos pos,
        double continents,
        double depth,
        Double erosion,
        Double ridges,
        Double temperature,
        Double vegetation
) {
    public Sample(ChunkPos pos, double continents, double depth) {
        this(pos, continents, depth, null, null, null, null);
    }

    public boolean hasFullDensities() {
        return erosion != null && ridges != null && temperature != null && vegetation != null;
    }

    public Sample withTemperature(double temperature) {
        return new Sample(pos, continents, depth, erosion, ridges, temperature, vegetation);
    }

    public Sample withErosion(double erosion) {
        return new Sample(pos, continents, depth, erosion, ridges, temperature, vegetation);
    }

    public Sample withRidges(double ridges) {
        return new Sample(pos, continents, depth, erosion, ridges, temperature, vegetation);
    }

    public Sample withVegetation(double vegetation) {
        return new Sample(pos, continents, depth, erosion, ridges, temperature, vegetation);
    }

    public boolean isOcean() {
        return continents < CommonConfig.get().oceanThreshold();
    }

    public double ridgesFolded() {
        if (ridges == null) {
            throw new IllegalStateException("ridgesFolded() called on sample without ridges data");
        }
        return -3.0 * (Math.abs(Math.abs(-ridges) - 0.6666666) - 0.3333333);
    }

    public int estimatedTerrainHeight() {
        return (int) Math.round(70 + 144 * depth);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.toLong());
        tag.putDouble("continents", continents);
        tag.putDouble("depth", depth);
        if (erosion != null) {
            tag.putDouble("erosion", erosion);
        }
        if (ridges != null) {
            tag.putDouble("ridges", ridges);
        }
        if (temperature != null) {
            tag.putDouble("temperature", temperature);
        }
        if (vegetation != null) {
            tag.putDouble("vegetation", vegetation);
        }
        return tag;
    }

    public static Sample decode(CompoundTag tag) {
        return new Sample(
            new ChunkPos(tag.getLong("pos")),
            tag.getDouble("continents"),
            tag.getDouble("depth"),
            tag.contains("erosion") ? tag.getDouble("erosion") : null,
            tag.contains("ridges") ? tag.getDouble("ridges") : null,
            tag.contains("temperature") ? tag.getDouble("temperature") : null,
            tag.contains("vegetation") ? tag.getDouble("vegetation") : null
        );
    }
}
