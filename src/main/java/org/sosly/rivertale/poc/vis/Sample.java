package org.sosly.rivertale.poc.vis;

import net.minecraft.world.level.biome.Biome;

public record Sample(
        int x,
        int z,
        Biome biome,
        double continents,
        double depth,
        double erosion,
        double ridges,
        double temperature,
        double vegetation
) {
    public long asLong() {
        return Sample.asLong(x, z);
    }

    public static long asLong(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
