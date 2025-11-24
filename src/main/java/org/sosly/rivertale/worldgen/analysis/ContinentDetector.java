package org.sosly.rivertale.worldgen.analysis;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import org.sosly.rivertale.data.ContinentDetectionResult;
import org.sosly.rivertale.data.ContinentId;
import org.sosly.rivertale.data.ContinentalData;
import org.sosly.rivertale.data.RegionKey;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Service for detecting continental structures using flood-fill algorithm.
 */
public class ContinentDetector {
    private static final int DEFAULT_MAX_RADIUS = 16384;
    private static final int DEFAULT_SAMPLE_STEP = 512;
    private static final int MAX_SAMPLES = 10000;
    private static final int FINE_SEARCH_RADIUS = 512;
    private static final int FINE_SEARCH_STEP = 64;
    private static final int SEA_LEVEL = 63;

    private final int maxRadius;
    private final int sampleStep;

    public ContinentDetector() {
        this(DEFAULT_MAX_RADIUS, DEFAULT_SAMPLE_STEP);
    }

    public ContinentDetector(int maxRadius, int sampleStep) {
        this.maxRadius = maxRadius;
        this.sampleStep = sampleStep;
    }

    /**
     * Find the continental center using flood-fill from the given position.
     *
     * @param startPos start position for the search
     * @param depthFunction Lithosphere's depth density function
     * @param erosionFunction Lithosphere's erosion density function
     * @return continental data with center and statistics
     */
    public ContinentalData findContinentCenter(BlockPos startPos,
                                                DensityFunction depthFunction,
                                                DensityFunction erosionFunction) {
        ContinentDetectionResult result = detectContinent(startPos, depthFunction, erosionFunction, null);
        return result.getContinentalData();
    }

    /**
     * Detect continent and return full result including visited regions.
     *
     * @param startPos start position for the search
     * @param depthFunction Lithosphere's depth density function
     * @param erosionFunction Lithosphere's erosion density function
     * @param level server level for height checking (null to skip fine search)
     * @return full detection result with center, statistics, and visited regions
     */
    public ContinentDetectionResult detectContinent(BlockPos startPos,
                                                     DensityFunction depthFunction,
                                                     DensityFunction erosionFunction,
                                                     ServerLevel level) {
        Queue<BlockPos> toSearch = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<RegionKey> visitedRegions = new HashSet<>();
        toSearch.add(startPos);

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        double maxContinentalness = -999.0;
        BlockPos bestPoint = startPos;
        int highPointCount = 0;
        int totalSamples = 0;

        while (!toSearch.isEmpty() && totalSamples < MAX_SAMPLES) {
            BlockPos current = toSearch.poll();

            if (visited.contains(current)) {
                continue;
            }
            if (Math.abs(current.getX() - startPos.getX()) > maxRadius) {
                continue;
            }
            if (Math.abs(current.getZ() - startPos.getZ()) > maxRadius) {
                continue;
            }

            visited.add(current);
            visitedRegions.add(RegionKey.fromBlockPos(current));
            totalSamples++;

            DensityFunction.SinglePointContext ctx =
                new DensityFunction.SinglePointContext(current.getX(), SEA_LEVEL, current.getZ());

            double depth = depthFunction.compute(ctx);
            double erosion = erosionFunction.compute(ctx);
            double continentalness = ContinentalnessCalculator.calculateContinentalness(depth, erosion);

            if (ContinentalnessCalculator.isOcean(continentalness)) {
                continue;
            }

            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());

            if (continentalness > 0.5) {
                highPointCount++;
            }

            if (continentalness > maxContinentalness) {
                maxContinentalness = continentalness;
                bestPoint = current;
            }

            if (ContinentalnessCalculator.isLand(continentalness)) {
                toSearch.add(current.north(sampleStep));
                toSearch.add(current.south(sampleStep));
                toSearch.add(current.east(sampleStep));
                toSearch.add(current.west(sampleStep));
                toSearch.add(new BlockPos(current.getX() + sampleStep, SEA_LEVEL, current.getZ() + sampleStep));
                toSearch.add(new BlockPos(current.getX() + sampleStep, SEA_LEVEL, current.getZ() - sampleStep));
                toSearch.add(new BlockPos(current.getX() - sampleStep, SEA_LEVEL, current.getZ() + sampleStep));
                toSearch.add(new BlockPos(current.getX() - sampleStep, SEA_LEVEL, current.getZ() - sampleStep));
            }
        }

        if (level != null) {
            bestPoint = refineWithHeightSearch(bestPoint, depthFunction, erosionFunction, level);
        }

        ContinentId continentId = computeContinentId(minX, maxX, minZ, maxZ, bestPoint);
        ContinentalData data = new ContinentalData(bestPoint, maxContinentalness, highPointCount, totalSamples);

        return new ContinentDetectionResult(data, visitedRegions, continentId);
    }

    private BlockPos refineWithHeightSearch(BlockPos coarseCenter,
                                             DensityFunction depthFunction,
                                             DensityFunction erosionFunction,
                                             ServerLevel level) {
        BlockPos bestPoint = coarseCenter;
        int bestHeight = level.getChunkSource().getGenerator().getBaseHeight(
            coarseCenter.getX(), coarseCenter.getZ(),
            Heightmap.Types.WORLD_SURFACE_WG,
            level, level.getChunkSource().randomState()
        );

        for (int dx = -FINE_SEARCH_RADIUS; dx <= FINE_SEARCH_RADIUS; dx += FINE_SEARCH_STEP) {
            for (int dz = -FINE_SEARCH_RADIUS; dz <= FINE_SEARCH_RADIUS; dz += FINE_SEARCH_STEP) {
                int x = coarseCenter.getX() + dx;
                int z = coarseCenter.getZ() + dz;

                DensityFunction.SinglePointContext ctx =
                    new DensityFunction.SinglePointContext(x, SEA_LEVEL, z);
                double depth = depthFunction.compute(ctx);
                double erosion = erosionFunction.compute(ctx);
                double continentalness = ContinentalnessCalculator.calculateContinentalness(depth, erosion);

                if (!ContinentalnessCalculator.isLand(continentalness)) {
                    continue;
                }

                int height = level.getChunkSource().getGenerator().getBaseHeight(
                    x, z,
                    Heightmap.Types.WORLD_SURFACE_WG,
                    level, level.getChunkSource().randomState()
                );

                if (height > bestHeight) {
                    bestHeight = height;
                    bestPoint = new BlockPos(x, SEA_LEVEL, z);
                }
            }
        }

        return bestPoint;
    }

    private ContinentId computeContinentId(int minX, int maxX, int minZ, int maxZ, BlockPos center) {
        long hash = 17;
        hash = hash * 31 + minX;
        hash = hash * 31 + maxX;
        hash = hash * 31 + minZ;
        hash = hash * 31 + maxZ;
        hash = hash * 31 + center.getX();
        hash = hash * 31 + center.getZ();
        return new ContinentId(hash);
    }
}
