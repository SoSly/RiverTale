package org.sosly.rivertale.worldgen;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;

public class RiverNoiseGenerator {
    private static final double RIDGE_FREQUENCY = 0.003;
    private static final double RIVER_THRESHOLD = 0.05;
    private static final int MIN_RIVER_SPACING = 256;

    private final SimplexNoise ridgeNoise;
    private final SimplexNoise spacingNoise;

    public RiverNoiseGenerator(long worldSeed) {
        RandomSource random = RandomSource.create(worldSeed);
        this.ridgeNoise = new SimplexNoise(random);
        this.spacingNoise = new SimplexNoise(RandomSource.create(worldSeed + 1));
    }

    public double calculateRiverPotential(double x, double z) {
        double scaledX = x * RIDGE_FREQUENCY;
        double scaledZ = z * RIDGE_FREQUENCY;
        double noise = ridgeNoise.getValue(scaledX, scaledZ);
        return 1.0 - Math.abs(noise);
    }

    public boolean hasRiver(double x, double z) {
        double potential = calculateRiverPotential(x, z);
        if (potential < RIVER_THRESHOLD) {
            return false;
        }

        return meetsMinimumSpacing(x, z);
    }

    private boolean meetsMinimumSpacing(double x, double z) {
        double spacingScale = 1.0 / MIN_RIVER_SPACING;
        double spacingValue = spacingNoise.getValue(x * spacingScale, z * spacingScale);
        return spacingValue > -0.3;
    }
}
