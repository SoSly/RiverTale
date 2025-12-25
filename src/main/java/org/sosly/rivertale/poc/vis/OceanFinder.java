package org.sosly.rivertale.poc.vis;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.poc.vis.metrics.Store;
import org.sosly.rivertale.poc.vis.metrics.Timer;

public class OceanFinder {
    private static final int CHUNK_SIZE = 16;
    private static final double OCEAN_CONTINENTALNESS_THRESHOLD = -0.17;

    private final SampleCache cache;
    private final RandomState randomState;
    private final BiomeSource biomeSource;
    private final int gridSize;
    private final int gridSizeInChunks;

    public OceanFinder(SampleCache cache, RandomState randomState, BiomeSource biomeSource, int gridSize) {
        this.cache = cache;
        this.randomState = randomState;
        this.biomeSource = biomeSource;
        this.gridSize = gridSize;
        this.gridSizeInChunks = Math.max(1, gridSize / CHUNK_SIZE);
    }

    public OceanBoundary findNearest(int fromX, int fromZ) {
        Timer.Record timer = Store.getTimer(OceanFinder.class, "findNearest").start();
        int startChunkX = fromX >> 4;
        int startChunkZ = fromZ >> 4;

        CoarseHit hit = spiralSearchCoarse(startChunkX, startChunkZ);
        OceanBoundary output = refineToChunkBoundary(startChunkX, startChunkZ, hit.chunkX, hit.chunkZ);
        timer.stop();
        return output;
    }

    private CoarseHit spiralSearchCoarse(int centerChunkX, int centerChunkZ) {
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;

        while (true) {
            int sampleChunkX = centerChunkX + (x * gridSizeInChunks);
            int sampleChunkZ = centerChunkZ + (z * gridSizeInChunks);

            if (isOceanChunk(sampleChunkX, sampleChunkZ)) {
                return new CoarseHit(sampleChunkX, sampleChunkZ);
            }

            if ((x == z) || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int temp = dx;
                dx = -dz;
                dz = temp;
            }

            x += dx;
            z += dz;
        }
    }

    private OceanBoundary refineToChunkBoundary(int landChunkX, int landChunkZ, int oceanChunkX, int oceanChunkZ) {
        int originalDiffX = Math.abs(oceanChunkX - landChunkX);
        int originalDiffZ = Math.abs(oceanChunkZ - landChunkZ);

        while (true) {
            int diffX = Math.abs(oceanChunkX - landChunkX);
            int diffZ = Math.abs(oceanChunkZ - landChunkZ);

            if (diffX <= 1 && diffZ <= 1) {
                break;
            }

            int midChunkX = (landChunkX + oceanChunkX) / 2;
            int midChunkZ = (landChunkZ + oceanChunkZ) / 2;

            if (isOceanChunk(midChunkX, midChunkZ)) {
                oceanChunkX = midChunkX;
                oceanChunkZ = midChunkZ;
            } else {
                landChunkX = midChunkX;
                landChunkZ = midChunkZ;
            }
        }

        int finalDiffX = Math.abs(oceanChunkX - landChunkX);
        int finalDiffZ = Math.abs(oceanChunkZ - landChunkZ);

        if (finalDiffX == 1 && finalDiffZ == 1) {
            if (originalDiffX >= originalDiffZ) {
                return refineAlongX(landChunkX, landChunkZ, oceanChunkX);
            } else {
                return refineAlongZ(landChunkX, landChunkZ, oceanChunkZ);
            }
        }

        return new OceanBoundary(
            chunkCenterX(landChunkX), chunkCenterZ(landChunkZ),
            chunkCenterX(oceanChunkX), chunkCenterZ(oceanChunkZ)
        );
    }

    private OceanBoundary refineAlongX(int landChunkX, int landChunkZ, int oceanChunkX) {
        int step = Integer.compare(oceanChunkX, landChunkX);
        int testX = landChunkX + step;

        if (isOceanChunk(testX, landChunkZ)) {
            return new OceanBoundary(
                chunkCenterX(landChunkX), chunkCenterZ(landChunkZ),
                chunkCenterX(testX), chunkCenterZ(landChunkZ)
            );
        }

        return new OceanBoundary(
            chunkCenterX(testX), chunkCenterZ(landChunkZ),
            chunkCenterX(oceanChunkX), chunkCenterZ(landChunkZ)
        );
    }

    private OceanBoundary refineAlongZ(int landChunkX, int landChunkZ, int oceanChunkZ) {
        int step = Integer.compare(oceanChunkZ, landChunkZ);
        int testZ = landChunkZ + step;

        if (isOceanChunk(landChunkX, testZ)) {
            return new OceanBoundary(
                chunkCenterX(landChunkX), chunkCenterZ(landChunkZ),
                chunkCenterX(landChunkX), chunkCenterZ(testZ)
            );
        }

        return new OceanBoundary(
            chunkCenterX(landChunkX), chunkCenterZ(testZ),
            chunkCenterX(landChunkX), chunkCenterZ(oceanChunkZ)
        );
    }

    public boolean isOceanChunk(int chunkX, int chunkZ) {
        int blockX = chunkCenterX(chunkX);
        int blockZ = chunkCenterZ(chunkZ);
        Sample sample = cache.getOrCompute(blockX, blockZ, randomState, biomeSource);
        return sample.continents() < OCEAN_CONTINENTALNESS_THRESHOLD;
    }

    public static int chunkCenterX(int chunkX) {
        return (chunkX << 4) + 8;
    }

    static int chunkCenterZ(int chunkZ) {
        return (chunkZ << 4) + 8;
    }

    private record CoarseHit(int chunkX, int chunkZ) {}
}
