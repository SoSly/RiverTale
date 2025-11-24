package org.sosly.rivertale.data;

import net.minecraft.core.BlockPos;

/**
 * Data class holding continental analysis results.
 */
public class ContinentalData {
    private final BlockPos center;
    private final double maxContinentalness;
    private final int highPointCount;
    private final int totalSamples;

    public ContinentalData(BlockPos center, double maxContinentalness, int highPointCount, int totalSamples) {
        this.center = center;
        this.maxContinentalness = maxContinentalness;
        this.highPointCount = highPointCount;
        this.totalSamples = totalSamples;
    }

    public BlockPos getCenter() {
        return center;
    }

    public double getMaxContinentalness() {
        return maxContinentalness;
    }

    public int getHighPointCount() {
        return highPointCount;
    }

    public int getTotalSamples() {
        return totalSamples;
    }
}
