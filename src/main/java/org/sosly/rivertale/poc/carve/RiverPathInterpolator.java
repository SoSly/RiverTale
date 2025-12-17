package org.sosly.rivertale.poc.carve;

import java.util.ArrayList;
import java.util.List;

public class RiverPathInterpolator {
    private final int entryX;
    private final int entryZ;
    private final int exitX;
    private final int exitZ;
    private final int distanceToOcean;
    private final int width;
    private final long seed;

    public RiverPathInterpolator(int entryX, int entryZ, int exitX, int exitZ, int distanceToOcean, int width, long seed) {
        this.entryX = entryX;
        this.entryZ = entryZ;
        this.exitX = exitX;
        this.exitZ = exitZ;
        this.distanceToOcean = distanceToOcean;
        this.width = width;
        this.seed = seed;
    }

    public List<PathPoint> generatePath() {
        double dx = exitX - entryX;
        double dz = exitZ - entryZ;
        double pathLength = Math.sqrt(dx * dx + dz * dz);

        if (pathLength < 1) {
            return List.of(new PathPoint(entryX, entryZ, calculateTargetElevation(), 0.0));
        }

        double dirX = dx / pathLength;
        double dirZ = dz / pathLength;
        double perpX = -dirZ;
        double perpZ = dirX;

        int numPoints = (int) Math.ceil(pathLength);
        List<PathPoint> points = new ArrayList<>(numPoints);

        for (int i = 0; i <= numPoints; i++) {
            double t = i / (double) numPoints;
            double baseX = entryX + t * dx;
            double baseZ = entryZ + t * dz;

            double offset = calculateMeanderOffset(baseX, baseZ, t);
            int x = (int) Math.round(baseX + perpX * offset);
            int z = (int) Math.round(baseZ + perpZ * offset);

            int targetElevation = calculateTargetElevation();
            points.add(new PathPoint(x, z, targetElevation, t));
        }

        return points;
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

    private int calculateTargetElevation() {
        return CarveConfig.SEA_LEVEL + (distanceToOcean * CarveConfig.ELEVATION_PER_CELL);
    }

    public record PathPoint(int x, int z, int targetElevation, double normalizedT) {}
}
