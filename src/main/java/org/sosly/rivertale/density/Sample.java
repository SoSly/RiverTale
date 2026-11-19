package org.sosly.rivertale.density;

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
}
