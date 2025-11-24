package org.sosly.rivertale.worldgen.analysis;

/**
 * Utility for calculating continentalness from Lithosphere's depth and erosion values.
 */
public final class ContinentalnessCalculator {
    private static final double DEPTH_WEIGHT = 0.7;
    private static final double EROSION_WEIGHT = 0.3;
    private static final double OCEAN_THRESHOLD = -0.2;
    private static final double LAND_THRESHOLD = -0.1;

    private ContinentalnessCalculator() {
    }

    /**
     * Calculate continentalness from depth and erosion values.
     *
     * @param depth the depth value from Lithosphere
     * @param erosion the erosion value from Lithosphere
     * @return continentalness value between -1 and 1
     */
    public static double calculateContinentalness(double depth, double erosion) {
        double edgeFromDepth = Math.tanh(depth * 2.0);
        double edgeFromErosion = (erosion + 1.0) * 0.5;
        double continentalness = edgeFromDepth * DEPTH_WEIGHT + edgeFromErosion * EROSION_WEIGHT;
        return Math.max(-1.0, Math.min(1.0, continentalness));
    }

    /**
     * Check if a continentalness value represents ocean.
     *
     * @param continentalness the continentalness value
     * @return true if this is ocean (barrier for rivers)
     */
    public static boolean isOcean(double continentalness) {
        return continentalness < OCEAN_THRESHOLD;
    }

    /**
     * Check if a continentalness value represents land.
     *
     * @param continentalness the continentalness value
     * @return true if this is land (can have rivers)
     */
    public static boolean isLand(double continentalness) {
        return continentalness > LAND_THRESHOLD;
    }

}
