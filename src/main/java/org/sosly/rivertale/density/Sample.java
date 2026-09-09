package org.sosly.rivertale.density;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.ChunkPos;

public record Sample(
        ChunkPos pos,
        double continents,
        int terrainHeight,
        boolean ocean,
        Double erosion,
        Double ridges,
        Double temperature,
        Double vegetation
) {
    public Sample(ChunkPos pos, double continents, int terrainHeight, boolean ocean) {
        this(pos, continents, terrainHeight, ocean, null, null, null, null);
    }

    public boolean hasFullDensities() {
        return erosion != null && ridges != null && temperature != null && vegetation != null;
    }

    public Sample withTemperature(double temperature) {
        return new Sample(pos, continents, terrainHeight, ocean, erosion, ridges, temperature, vegetation);
    }

    public Sample withErosion(double erosion) {
        return new Sample(pos, continents, terrainHeight, ocean, erosion, ridges, temperature, vegetation);
    }

    public Sample withRidges(double ridges) {
        return new Sample(pos, continents, terrainHeight, ocean, erosion, ridges, temperature, vegetation);
    }

    public Sample withVegetation(double vegetation) {
        return new Sample(pos, continents, terrainHeight, ocean, erosion, ridges, temperature, vegetation);
    }

    public boolean isOcean() {
        return ocean;
    }

    public double ridgesFolded() {
        if (ridges == null) {
            throw new IllegalStateException("ridgesFolded() called on sample without ridges data");
        }
        return -3.0 * (Math.abs(Math.abs(-ridges) - 0.6666666) - 0.3333333);
    }

    public int estimatedTerrainHeight() {
        return terrainHeight;
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.toLong());
        tag.putDouble("continents", continents);
        tag.putInt("terrainHeight", terrainHeight);
        tag.putBoolean("ocean", ocean);
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
        if (!tag.contains("terrainHeight", Tag.TAG_INT) || !tag.contains("ocean", Tag.TAG_BYTE)) {
            throw new IllegalArgumentException("Sample is missing RTF terrain height or ocean data");
        }
        return new Sample(
            new ChunkPos(tag.getLong("pos")),
            tag.getDouble("continents"),
            tag.getInt("terrainHeight"),
            tag.getBoolean("ocean"),
            tag.contains("erosion") ? tag.getDouble("erosion") : null,
            tag.contains("ridges") ? tag.getDouble("ridges") : null,
            tag.contains("temperature") ? tag.getDouble("temperature") : null,
            tag.contains("vegetation") ? tag.getDouble("vegetation") : null
        );
    }
}
