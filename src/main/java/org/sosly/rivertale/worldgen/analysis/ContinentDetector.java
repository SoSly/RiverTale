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
    private static final int DEFAULT_SAMPLE_STEP = 16;
    private static final int MAX_SAMPLES = 100000;
    private static final int FINE_SEARCH_RADIUS = 512;
    private static final int FINE_SEARCH_STEP = 128;
    private static final int SEA_LEVEL = 63;
    private static final double CONTINENT_THRESHOLD = -0.13;

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
     * @param continentsFunction Lithosphere's continents density function (for boundaries)
     * @param depthFunction Lithosphere's depth density function (for finding peaks)
     * @return continental data with center and statistics
     */
    public ContinentalData findContinentCenter(BlockPos startPos,
                                                DensityFunction continentsFunction,
                                                DensityFunction depthFunction) {
        ContinentDetectionResult result = detectContinent(startPos, continentsFunction, depthFunction, null);
        return result.getContinentalData();
    }

    /**
     * Detect continent and return full result including visited regions.
     *
     * @param startPos start position for the search
     * @param continentsFunction Lithosphere's continents density function (for boundaries)
     * @param depthFunction Lithosphere's depth density function (for finding peaks)
     * @param level server level for height checking (null to skip fine search)
     * @return full detection result with center, statistics, and visited regions
     */
    public ContinentDetectionResult detectContinent(BlockPos startPos,
                                                     DensityFunction continentsFunction,
                                                     DensityFunction depthFunction,
                                                     ServerLevel level) {
        BlockPos landPos = findNearestLand(startPos, continentsFunction);
        if (landPos == null) {
            return ContinentDetectionResult.empty();
        }

        Queue<BlockPos> toSearch = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        Set<RegionKey> visitedRegions = new HashSet<>();
        toSearch.add(landPos);

        visitedRegions.add(RegionKey.fromBlockPos(startPos));

        double maxDepth = -999.0;
        BlockPos bestPoint = landPos;
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
            totalSamples++;

            DensityFunction.SinglePointContext ctx =
                new DensityFunction.SinglePointContext(current.getX(), SEA_LEVEL, current.getZ());

            double continentalness = continentsFunction.compute(ctx);

            if (continentalness < CONTINENT_THRESHOLD) {
                continue;
            }

            visitedRegions.add(RegionKey.fromBlockPos(current));

            double depth = depthFunction.compute(ctx);

            if (depth > 1.0) {
                highPointCount++;
            }

            if (depth > maxDepth) {
                maxDepth = depth;
                bestPoint = current;
            }

            toSearch.add(current.north(sampleStep));
            toSearch.add(current.south(sampleStep));
            toSearch.add(current.east(sampleStep));
            toSearch.add(current.west(sampleStep));
            toSearch.add(new BlockPos(current.getX() + sampleStep, SEA_LEVEL, current.getZ() + sampleStep));
            toSearch.add(new BlockPos(current.getX() + sampleStep, SEA_LEVEL, current.getZ() - sampleStep));
            toSearch.add(new BlockPos(current.getX() - sampleStep, SEA_LEVEL, current.getZ() + sampleStep));
            toSearch.add(new BlockPos(current.getX() - sampleStep, SEA_LEVEL, current.getZ() - sampleStep));
        }

        if (level != null) {
            bestPoint = refineWithHeightSearch(bestPoint, continentsFunction, level);
        }

        ContinentId continentId = computeContinentId(visitedRegions);
        ContinentalData data = new ContinentalData(bestPoint, maxDepth, highPointCount, totalSamples);

        return new ContinentDetectionResult(data, visitedRegions, continentId);
    }

    private BlockPos refineWithHeightSearch(BlockPos coarseCenter,
                                             DensityFunction continentsFunction,
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
                double continentalness = continentsFunction.compute(ctx);

                if (continentalness < CONTINENT_THRESHOLD) {
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

    private BlockPos findNearestLand(BlockPos startPos, DensityFunction continentsFunction) {
        DensityFunction.SinglePointContext startCtx =
            new DensityFunction.SinglePointContext(startPos.getX(), SEA_LEVEL, startPos.getZ());
        double startContinentalness = continentsFunction.compute(startCtx);

        if (startContinentalness >= CONTINENT_THRESHOLD) {
            return startPos;
        }

        for (int radius = sampleStep; radius <= maxRadius; radius += sampleStep) {
            for (int dx = -radius; dx <= radius; dx += sampleStep) {
                for (int dz = -radius; dz <= radius; dz += sampleStep) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    int x = startPos.getX() + dx;
                    int z = startPos.getZ() + dz;

                    DensityFunction.SinglePointContext ctx =
                        new DensityFunction.SinglePointContext(x, SEA_LEVEL, z);
                    double continentalness = continentsFunction.compute(ctx);

                    if (continentalness >= CONTINENT_THRESHOLD) {
                        return new BlockPos(x, SEA_LEVEL, z);
                    }
                }
            }
        }

        return null;
    }

    private ContinentId computeContinentId(Set<RegionKey> visitedRegions) {
        RegionKey minRegion = visitedRegions.stream()
            .min((a, b) -> {
                int cmpX = Integer.compare(a.getRegionX(), b.getRegionX());
                if (cmpX != 0) {
                    return cmpX;
                }
                return Integer.compare(a.getRegionZ(), b.getRegionZ());
            })
            .orElseThrow();

        long hash = 17;
        hash = hash * 31 + minRegion.getRegionX();
        hash = hash * 31 + minRegion.getRegionZ();
        return new ContinentId(hash);
    }
}
