package org.sosly.rivertale.poc.carve;

import java.util.ArrayList;
import java.util.List;

public class RiverPathInterpolator {
    private final int entryX;
    private final int entryZ;
    private final int exitX;
    private final int exitZ;
    private final int entryY;
    private final int exitY;
    private final double controlX;
    private final double controlZ;
    private final int width;
    private final double slope;
    private final long seed;

    public RiverPathInterpolator(int entryX, int entryZ, int exitX, int exitZ, int entryDist, int exitDist, CardinalDirection direction, int accumulation, long seed) {
        this.entryX = entryX;
        this.entryZ = entryZ;
        this.exitX = exitX;
        this.exitZ = exitZ;
        this.entryY = CarveConfig.SEA_LEVEL + entryDist * CarveConfig.ELEVATION_PER_CELL;
        this.exitY = CarveConfig.SEA_LEVEL + exitDist * CarveConfig.ELEVATION_PER_CELL;
        this.seed = seed;

        double totalDist = Math.sqrt((exitX - entryX) * (exitX - entryX) + (exitZ - entryZ) * (exitZ - entryZ));
        double controlDist = totalDist * CarveConfig.BEZIER_CONTROL_RATIO;
        this.controlX = entryX + direction.dx * controlDist;
        this.controlZ = entryZ + direction.dz * controlDist;

        double run = totalDist;
        double fall = Math.abs(entryY - exitY);
        this.slope = (run > 0) ? fall / run : 0;

        double baseWidth = CarveConfig.MIN_WIDTH + CarveConfig.WIDTH_PER_ACC_LOG * Math.log1p(accumulation);
        double slopeModifier = 1.0 / (1.0 + slope * CarveConfig.SLOPE_WIDTH_FACTOR);
        this.width = Math.max(CarveConfig.MIN_WIDTH, Math.min(CarveConfig.MAX_WIDTH, (int) Math.round(baseWidth * slopeModifier)));
    }

    public List<PathPoint> generatePath() {
        double dx = exitX - entryX;
        double dz = exitZ - entryZ;
        double pathLength = Math.sqrt(dx * dx + dz * dz);

        if (pathLength < 1) {
            return List.of(new PathPoint(entryX, entryZ, entryY, 0.0));
        }

        int numPoints = (int) Math.ceil(pathLength);
        List<PathPoint> points = new ArrayList<>(numPoints);

        for (int i = 0; i <= numPoints; i++) {
            double t = i / (double) numPoints;

            double baseX = (1 - t) * (1 - t) * entryX + 2 * (1 - t) * t * controlX + t * t * exitX;
            double baseZ = (1 - t) * (1 - t) * entryZ + 2 * (1 - t) * t * controlZ + t * t * exitZ;

            double tangentX = 2 * (1 - t) * (controlX - entryX) + 2 * t * (exitX - controlX);
            double tangentZ = 2 * (1 - t) * (controlZ - entryZ) + 2 * t * (exitZ - controlZ);
            double len = Math.sqrt(tangentX * tangentX + tangentZ * tangentZ);

            double perpX = 0;
            double perpZ = 0;
            if (len > 0) {
                perpX = -tangentZ / len;
                perpZ = tangentX / len;
            }

            double offset = calculateMeanderOffset(baseX, baseZ, t);
            int x = (int) Math.round(baseX + perpX * offset);
            int z = (int) Math.round(baseZ + perpZ * offset);

            int targetElevation = calculateCatenaryElevation(t);
            points.add(new PathPoint(x, z, targetElevation, t));
        }

        return points;
    }

    private int calculateCatenaryElevation(double t) {
        double linear = entryY + t * (exitY - entryY);
        double elevDiff = Math.abs(entryY - exitY);
        double sag = elevDiff * CarveConfig.CATENARY_RATIO * t * (1.0 - t);
        return (int) Math.round(linear - sag);
    }

    private double calculateMeanderOffset(double x, double z, double t) {
        double fadeMultiplier = calculateFadeMultiplier(t);
        double noise = sampleNoise(x * CarveConfig.NOISE_FREQUENCY, z * CarveConfig.NOISE_FREQUENCY);
        double maxOffset = CarveConfig.MEANDER_SCALE * width;
        return noise * maxOffset * fadeMultiplier;
    }

    private double calculateFadeMultiplier(double t) {
        if (t < CarveConfig.FADE_START) {
            return t / CarveConfig.FADE_START;
        }
        if (t > CarveConfig.FADE_END) {
            return (1.0 - t) / (1.0 - CarveConfig.FADE_END);
        }
        return 1.0;
    }

    private double sampleNoise(double x, double z) {
        long hash = hash2D(seed, (long) (x * 1000), (long) (z * 1000));
        return (hash & 0xFFFFFFL) / (double) 0xFFFFFFL * 2.0 - 1.0;
    }

    private static long hash2D(long seed, long x, long z) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = Long.rotateLeft(h, 31);
        h *= 0xC6A4A7935BD1E995L;
        h ^= z * 0x85EBCA6B;
        h = Long.rotateLeft(h, 27);
        h *= 0xC2B2AE3D27D4EB4FL;
        return h;
    }

    public int getWidth() {
        return width;
    }

    public double getSlope() {
        return slope;
    }

    public record PathPoint(int x, int z, int targetElevation, double normalizedT) {}
}
