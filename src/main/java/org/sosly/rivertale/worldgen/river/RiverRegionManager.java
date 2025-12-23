package org.sosly.rivertale.worldgen.river;

import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sosly.rivertale.config.RiverConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class RiverRegionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiverRegionManager.class);
    private static final int MAX_RECURSION_DEPTH = 1000;

    static RiverRegion createRegion(RiverRegionKey key, DensityProvider provider, RandomState randomState) {
        long startTime = System.nanoTime();

        double[][] cellDensities = provider.sampleCellDensities(key.worldX(), key.worldZ(), RiverRegionKey.getRegionSize());
        double density = provider.getAveragedDensity(key.worldX(), key.worldZ(), RiverRegionKey.getRegionSize());
        boolean participating = ParticipationCalculator.isParticipating(key, randomState);

        RiverRegion region = new RiverRegion(key, density, cellDensities, participating);

        RegionFeatureType terrain = classifyTerrain(key, provider);
        if (participating && terrain != RegionFeatureType.BODY) {
            computeFlow(region, terrain, provider, randomState);
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        LOGGER.debug("Region ({}, {}) created in {}ms [{}]", key.regionX(), key.regionZ(), elapsedMs, terrain);

        return region;
    }

    public static RegionFeatureType classifyTerrain(RiverRegionKey key, DensityProvider provider) {
        int regionSize = RiverRegionKey.getRegionSize();
        double step = regionSize / 8.0;

        boolean hasWater = false;
        boolean hasLand = false;

        for (int i = 0; i < 8 && (!hasWater || !hasLand); i++) {
            for (int j = 0; j < 8 && (!hasWater || !hasLand); j++) {
                int sampleX = key.worldX() + (int) ((i + 0.5) * step);
                int sampleZ = key.worldZ() + (int) ((j + 0.5) * step);

                if (provider.isOcean(sampleX, sampleZ) || provider.isLake(sampleX, sampleZ)) {
                    hasWater = true;
                } else {
                    hasLand = true;
                }
            }
        }

        if (hasWater && hasLand) {
            return RegionFeatureType.SHORE;
        }
        if (hasWater) {
            return RegionFeatureType.BODY;
        }

        return null;
    }

    private static void computeFlow(RiverRegion region, RegionFeatureType terrain, DensityProvider provider, RandomState randomState) {
        if (terrain == RegionFeatureType.BODY) {
            region.setPrimaryOutput(PathDirection.NONE);
            return;
        }

        if (terrain == RegionFeatureType.SHORE) {
            region.setPrimaryOutput(PathDirection.NONE);
            return;
        }

        RiverRegionKey key = region.getKey();
        RegionDensityProvider cdp = (RegionDensityProvider) provider;

        BiFunction<Integer, Integer, Double> densitySampler = cdp::getDensity;
        BiFunction<Integer, Integer, Double> continentsSampler = cdp::getContinents;
        BiFunction<Integer, Integer, Double> depthSampler = cdp::getDepth;

        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        FlowDirection[][] flowDirections = D8FlowCalculator.computeFlowDirections(key, densitySampler);

        FlowDirection[][][] neighborFlowDirections = new FlowDirection[4][][];
        RegionFeatureType[] neighborFeatureTypes = new RegionFeatureType[4];
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (int i = 0; i < 4; i++) {
            RiverRegionKey neighborKey = cardinals[i].neighbor(key);
            neighborFlowDirections[i] = D8FlowCalculator.computeFlowDirections(neighborKey, densitySampler);
            RegionFeatureType neighborTerrain = classifyTerrain(neighborKey, provider);
            neighborFeatureTypes[i] = neighborTerrain != null ? neighborTerrain : RegionFeatureType.DIVIDE;
        }

        RegionFeatureType featureType = terrain != null ? terrain : RegionFeatureType.DIVIDE;
        D8FlowResult result = D8PathRefiner.refine(
            key, flowDirections, featureType,
            neighborFlowDirections, neighborFeatureTypes,
            densitySampler, continentsSampler, depthSampler,
            oceanThreshold, lakeThreshold);

        region.setPrimaryOutput(result.primaryOutputDirection());

        Set<PathDirection> secondaries = new HashSet<>();
        EdgeCrossing[] crossings = result.crossings();
        for (int i = 0; i < 4; i++) {
            if (crossings[i] != null && crossings[i].direction() == EdgeCrossing.Direction.OUT) {
                PathDirection dir = cardinals[i];
                if (dir != result.primaryOutputDirection()) {
                    secondaries.add(dir);
                }
            }
        }
        region.setSecondaryOutputs(secondaries);
    }

    public static RiverRegion createRegionFor(RiverRegionKey key, DensityProvider provider, RandomState randomState) {
        return createRegion(key, provider, randomState);
    }

    public static Map<PathDirection, List<int[]>> computePaths(RiverRegion region, DensityProvider provider, RandomState randomState) {
        return computeFlowResult(region.getKey(), provider, randomState).riverPaths();
    }

    public static D8FlowResult computeFlowResult(RiverRegionKey key, DensityProvider provider, RandomState randomState) {
        RegionDensityProvider cdp = (RegionDensityProvider) provider;

        BiFunction<Integer, Integer, Double> densitySampler = cdp::getDensity;
        BiFunction<Integer, Integer, Double> continentsSampler = cdp::getContinents;
        BiFunction<Integer, Integer, Double> depthSampler = cdp::getDepth;

        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        FlowDirection[][] flowDirections = D8FlowCalculator.computeFlowDirections(key, densitySampler);
        RegionFeatureType terrain = classifyTerrain(key, provider);
        RegionFeatureType featureType = terrain != null ? terrain : RegionFeatureType.DIVIDE;

        FlowDirection[][][] neighborFlowDirections = new FlowDirection[4][][];
        RegionFeatureType[] neighborFeatureTypes = loadNeighborFeatures(key, provider);
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (int i = 0; i < 4; i++) {
            RiverRegionKey neighborKey = cardinals[i].neighbor(key);
            neighborFlowDirections[i] = D8FlowCalculator.computeFlowDirections(neighborKey, densitySampler);
        }

        D8FlowResult result = D8PathRefiner.refine(
            key, flowDirections, featureType,
            neighborFlowDirections, neighborFeatureTypes,
            densitySampler, continentsSampler, depthSampler,
            oceanThreshold, lakeThreshold);

        return RiverPathValidator.validate(result, key, featureType, provider, randomState);
    }

    public static RegionFeatureType[] loadNeighborFeatures(RiverRegionKey key, DensityProvider provider) {
        RegionFeatureType[] neighborFeatureTypes = new RegionFeatureType[4];
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (int i = 0; i < cardinals.length; i++) {
            RiverRegionKey neighborKey = cardinals[i].neighbor(key);
            RegionFeatureType terrain = classifyTerrain(neighborKey, provider);
            neighborFeatureTypes[i] = terrain != null ? terrain : RegionFeatureType.DIVIDE;
        }

        return neighborFeatureTypes;
    }

    public static int getDistanceToTerminus(RiverRegion region, DensityProvider provider, RandomState randomState) {
        return getDistanceToTerminusRecursive(region, provider, randomState, 0);
    }

    private static int getDistanceToTerminusRecursive(RiverRegion region, DensityProvider provider, RandomState randomState, int depth) {
        RegionFeatureType featureType = getRegionFeatureType(region, provider, randomState);
        if (featureType == RegionFeatureType.BODY
                || featureType == RegionFeatureType.SHORE
                || featureType == RegionFeatureType.BASIN) {
            return 0;
        }

        if (depth > MAX_RECURSION_DEPTH) {
            LOGGER.warn("Distance calculation exceeded max depth at region {}", region.getKey());
            return depth;
        }

        if (!region.isParticipating() || region.getPrimaryOutput() == PathDirection.NONE) {
            return 0;
        }

        PathDirection primaryOutput = region.getPrimaryOutput();
        RiverRegionKey downstreamKey = primaryOutput.neighbor(region.getKey());
        RiverRegion downstream = createRegion(downstreamKey, provider, randomState);

        int downstreamDistance = getDistanceToTerminusRecursive(downstream, provider, randomState, depth + 1);
        return 1 + downstreamDistance;
    }

    public static int getUpstreamCount(RiverRegionKey key, DensityProvider provider, RandomState randomState) {
        Set<RiverRegionKey> visited = new HashSet<>();
        return countUpstream(key, 0, visited, provider, randomState);
    }

    public static RegionFeatureType getRegionFeatureType(RiverRegion region, DensityProvider provider, RandomState randomState) {
        RiverRegionKey key = region.getKey();
        int regionSize = RiverRegionKey.getRegionSize();
        double step = regionSize / 8.0;
        boolean hasWater = false;
        boolean hasLand = false;

        for (int i = 0; i < 8 && (!hasWater || !hasLand); i++) {
            for (int j = 0; j < 8 && (!hasWater || !hasLand); j++) {
                int sampleX = key.worldX() + (int) ((i + 0.5) * step);
                int sampleZ = key.worldZ() + (int) ((j + 0.5) * step);
                if (provider.isOcean(sampleX, sampleZ) || provider.isLake(sampleX, sampleZ)) {
                    hasWater = true;
                } else {
                    hasLand = true;
                }
            }
        }

        if (hasWater && hasLand) {
            return RegionFeatureType.SHORE;
        }
        if (hasWater) {
            return RegionFeatureType.BODY;
        }

        if (!region.isParticipating()) {
            return RegionFeatureType.BARREN;
        }

        if (region.getPrimaryOutput() == PathDirection.NONE && region.getSecondaryOutputs().isEmpty()) {
            return RegionFeatureType.BASIN;
        }

        int upstreamCount = getUpstreamCount(key, provider, randomState);
        return upstreamCount == 0 ? RegionFeatureType.DIVIDE : RegionFeatureType.FLUVIAL;
    }

    public static CellFeatureType getCellFeatureType(RiverRegion region, int row, int col, Map<PathDirection, List<int[]>> paths, DensityProvider provider) {
        if (!isCellOnRiverPath(paths, row, col)) {
            return null;
        }

        int inletCount = countInlets(paths, row, col);

        if (isTerminusCell(region, paths, row, col, provider)) {
            return CellFeatureType.TERMINUS;
        }
        if (isSourceCell(paths, row, col, inletCount)) {
            return CellFeatureType.SOURCE;
        }
        if (inletCount >= 2) {
            return CellFeatureType.JUNCTION;
        }
        return CellFeatureType.COURSE;
    }

    private static int countInlets(Map<PathDirection, List<int[]>> paths, int row, int col) {
        Set<Long> uniqueInlets = new HashSet<>();
        for (List<int[]> path : paths.values()) {
            for (int i = 1; i < path.size(); i++) {
                int[] current = path.get(i);
                if (current[0] == row && current[1] == col) {
                    int[] previous = path.get(i - 1);
                    long key = ((long) previous[0] << 32) | (previous[1] & 0xFFFFFFFFL);
                    uniqueInlets.add(key);
                }
            }
        }
        return uniqueInlets.size();
    }

    private static boolean isCellOnRiverPath(Map<PathDirection, List<int[]>> paths, int row, int col) {
        for (List<int[]> path : paths.values()) {
            for (int[] point : path) {
                if (point[0] == row && point[1] == col) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTerminusCell(RiverRegion region, Map<PathDirection, List<int[]>> paths, int row, int col, DensityProvider provider) {
        RegionFeatureType terrain = classifyTerrain(region.getKey(), provider);
        boolean isShore = terrain == RegionFeatureType.SHORE;
        boolean isBasin = region.getPrimaryOutput() == PathDirection.NONE
            && region.getSecondaryOutputs().isEmpty();

        if (!isShore && !isBasin) {
            return false;
        }

        for (List<int[]> path : paths.values()) {
            if (path.isEmpty()) {
                continue;
            }
            int[] lastPoint = path.get(path.size() - 1);
            if (lastPoint[0] == row && lastPoint[1] == col) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSourceCell(Map<PathDirection, List<int[]>> paths, int row, int col, int inletCount) {
        if (inletCount > 0) {
            return false;
        }

        for (Map.Entry<PathDirection, List<int[]>> entry : paths.entrySet()) {
            List<int[]> path = entry.getValue();
            if (path.isEmpty()) {
                continue;
            }
            int[] firstPoint = path.get(0);
            if (firstPoint[0] == row && firstPoint[1] == col) {
                return true;
            }
        }
        return false;
    }

    private static int countUpstream(RiverRegionKey key, int depth, Set<RiverRegionKey> visited, DensityProvider provider, RandomState randomState) {
        if (depth > RiverConfig.UPSTREAM_DEPTH_LIMIT.get()) {
            return 0;
        }
        if (visited.contains(key)) {
            return 0;
        }
        visited.add(key);

        int count = 0;
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (PathDirection dir : cardinals) {
            RiverRegionKey neighborKey = dir.neighbor(key);
            RiverRegion neighbor = createRegion(neighborKey, provider, randomState);

            if (!neighbor.isParticipating()) {
                continue;
            }

            PathDirection neighborOutput = neighbor.getPrimaryOutput();
            if (neighborOutput == null || neighborOutput == PathDirection.NONE) {
                continue;
            }

            RiverRegionKey outputTarget = neighborOutput.neighbor(neighborKey);
            if (outputTarget != null && outputTarget.equals(key)) {
                count += 1 + countUpstream(neighborKey, depth + 1, visited, provider, randomState);
            }
        }

        return count;
    }
}
