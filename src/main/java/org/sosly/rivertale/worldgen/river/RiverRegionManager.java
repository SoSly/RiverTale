package org.sosly.rivertale.worldgen.river;

import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sosly.rivertale.config.RiverConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;

public class RiverRegionManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiverRegionManager.class);
    private static final int MAX_RECURSION_DEPTH = 1000;

    static RiverRegion createRegion(RiverRegionKey key, DensityProvider provider, RandomState randomState) {
        long startTime = System.nanoTime();

        double[][] cellDensities = provider.sampleCellDensities(key.worldX(), key.worldZ(), RiverRegionKey.getRegionSize());
        double density = provider.getAveragedDensity(key.worldX(), key.worldZ(), RiverRegionKey.getRegionSize());
        RegionClassification classification = classifyRegion(key, provider);
        boolean participating = ParticipationCalculator.isParticipating(key, randomState);

        RiverRegion region = new RiverRegion(key, density, cellDensities, classification, participating);

        if (participating && classification == RegionClassification.LAND) {
            computeFlow(region, provider, randomState);
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        LOGGER.debug("Region ({}, {}) created in {}ms [{}]", key.regionX(), key.regionZ(), elapsedMs, classification);

        return region;
    }

    private static RegionClassification classifyRegion(RiverRegionKey key, DensityProvider provider) {
        int regionSize = RiverRegionKey.getRegionSize();
        double step = regionSize / 8.0;

        boolean hasOcean = false;
        boolean hasLake = false;
        boolean hasLand = false;

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int sampleX = key.worldX() + (int) (i * step);
                int sampleZ = key.worldZ() + (int) (j * step);

                if (provider.isOcean(sampleX, sampleZ)) {
                    hasOcean = true;
                } else if (provider.isLake(sampleX, sampleZ)) {
                    hasLake = true;
                } else {
                    hasLand = true;
                }
            }
        }

        if (hasOcean && (hasLand || hasLake)) {
            return RegionClassification.COASTAL;
        }
        if (hasOcean) {
            return RegionClassification.OCEAN;
        }
        if (hasLake && hasLand) {
            return RegionClassification.LAKESHORE;
        }
        if (hasLake) {
            return RegionClassification.LAKE;
        }

        return RegionClassification.LAND;
    }

    private static void computeFlow(RiverRegion region, DensityProvider provider, RandomState randomState) {
        RegionClassification classification = region.getClassification();

        if (classification == RegionClassification.OCEAN || classification == RegionClassification.LAKE) {
            region.setPrimaryOutput(PathDirection.NONE);
            return;
        }

        if (classification == RegionClassification.COASTAL || classification == RegionClassification.LAKESHORE) {
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
        RegionClassification[] neighborClassifications = new RegionClassification[4];
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (int i = 0; i < 4; i++) {
            RiverRegionKey neighborKey = cardinals[i].neighbor(key);
            neighborFlowDirections[i] = D8FlowCalculator.computeFlowDirections(neighborKey, densitySampler);
            neighborClassifications[i] = D8FlowCalculator.classifyRegion(
                neighborKey, continentsSampler, depthSampler, oceanThreshold, lakeThreshold);
        }

        D8FlowResult result = D8PathRefiner.refine(
            key, flowDirections, classification,
            neighborFlowDirections, neighborClassifications,
            densitySampler, continentsSampler, depthSampler,
            oceanThreshold, lakeThreshold);

        region.setPrimaryOutput(result.primaryOutputDirection());
        region.setBasin(result.isBasin());

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

    public static RiverRegion getOrCreate(RiverRegionKey key, DensityProvider provider, RandomState randomState) {
        return RiverRegionCache.getOrCompute(key, k -> createRegion(k, provider, randomState));
    }

    public static void ensurePaths(RiverRegion region, DensityProvider provider, RandomState randomState) {
        if (!region.getRiverPaths().isEmpty()) {
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
        RegionClassification classification = region.getClassification();

        FlowDirection[][][] neighborFlowDirections = new FlowDirection[4][][];
        RegionClassification[] neighborClassifications = ensureNeighborsLoaded(key, provider, randomState);
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (int i = 0; i < 4; i++) {
            RiverRegionKey neighborKey = cardinals[i].neighbor(key);
            neighborFlowDirections[i] = D8FlowCalculator.computeFlowDirections(neighborKey, densitySampler);
        }

        D8FlowResult result = D8PathRefiner.refine(
            key, flowDirections, classification,
            neighborFlowDirections, neighborClassifications,
            densitySampler, continentsSampler, depthSampler,
            oceanThreshold, lakeThreshold);

        region.setRiverPaths(result.riverPaths());
    }

    public static RegionClassification[] ensureNeighborsLoaded(RiverRegionKey key, DensityProvider provider, RandomState randomState) {
        RegionClassification[] neighborClassifications = new RegionClassification[4];
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (int i = 0; i < cardinals.length; i++) {
            RiverRegionKey neighborKey = cardinals[i].neighbor(key);
            RiverRegion neighbor = getOrCreate(neighborKey, provider, randomState);
            neighborClassifications[i] = neighbor.getClassification();
        }

        return neighborClassifications;
    }

    public static int getDistanceToTerminus(RiverRegion region, DensityProvider provider, RandomState randomState) {
        return getDistanceToTerminusRecursive(region, provider, randomState, 0);
    }

    private static int getDistanceToTerminusRecursive(RiverRegion region, DensityProvider provider, RandomState randomState, int depth) {
        if (region.getDistanceToTerminus() >= 0) {
            return region.getDistanceToTerminus();
        }

        RegionClassification classification = region.getClassification();
        if (classification == RegionClassification.OCEAN
                || classification == RegionClassification.COASTAL
                || classification == RegionClassification.LAKE
                || classification == RegionClassification.LAKESHORE
                || region.isBasin()) {
            region.setDistanceToTerminus(0);
            return 0;
        }

        if (depth > MAX_RECURSION_DEPTH) {
            LOGGER.warn("Distance calculation exceeded max depth at region {}", region.getKey());
            return depth;
        }

        if (!region.isParticipating() || region.getPrimaryOutput() == PathDirection.NONE) {
            region.setDistanceToTerminus(0);
            return 0;
        }

        PathDirection primaryOutput = region.getPrimaryOutput();
        RiverRegionKey downstreamKey = primaryOutput.neighbor(region.getKey());
        RiverRegion downstream = RiverRegionCache.getOrCompute(downstreamKey, k -> createRegion(k, provider, randomState));

        int downstreamDistance = getDistanceToTerminusRecursive(downstream, provider, randomState, depth + 1);
        int distance = 1 + downstreamDistance;

        region.setDistanceToTerminus(distance);
        return distance;
    }

    public static int getUpstreamCount(RiverRegionKey key, DensityProvider provider, RandomState randomState) {
        Set<RiverRegionKey> visited = new HashSet<>();
        return countUpstream(key, 0, visited, provider, randomState);
    }

    public static RegionFeatureType getRegionFeatureType(RiverRegion region, DensityProvider provider, RandomState randomState) {
        RegionClassification classification = region.getClassification();

        if (classification == RegionClassification.OCEAN || classification == RegionClassification.LAKE) {
            return RegionFeatureType.BODY;
        }
        if (classification == RegionClassification.COASTAL || classification == RegionClassification.LAKESHORE) {
            return RegionFeatureType.SHORE;
        }
        if (!region.isParticipating()) {
            return RegionFeatureType.BARREN;
        }
        if (region.isBasin()) {
            return RegionFeatureType.BASIN;
        }

        int upstreamCount = getUpstreamCount(region.getKey(), provider, randomState);

        if (upstreamCount == 0) {
            return RegionFeatureType.DIVIDE;
        }

        return RegionFeatureType.FLUVIAL;
    }

    public static CellFeatureType getCellFeatureType(RiverRegion region, int row, int col, FlowDirection[][] flowDirections) {
        int inletCount = countInlets(flowDirections, row, col);
        boolean isOnPath = isCellOnRiverPath(region, row, col);

        if (!isOnPath) {
            return CellFeatureType.COURSE;
        }

        if (isTerminusCell(region, row, col)) {
            return CellFeatureType.TERMINUS;
        }
        if (isSourceCell(region, row, col, flowDirections, inletCount)) {
            return CellFeatureType.SOURCE;
        }
        if (inletCount >= 2) {
            return CellFeatureType.JUNCTION;
        }
        return CellFeatureType.COURSE;
    }

    private static int countInlets(FlowDirection[][] flowDirections, int row, int col) {
        int count = 0;
        int[][] neighbors = {
            {row - 1, col}, {row + 1, col}, {row, col - 1}, {row, col + 1},
            {row - 1, col - 1}, {row - 1, col + 1}, {row + 1, col - 1}, {row + 1, col + 1}
        };
        FlowDirection[] expectedDirs = {
            FlowDirection.SOUTH, FlowDirection.NORTH, FlowDirection.EAST, FlowDirection.WEST,
            FlowDirection.SOUTHEAST, FlowDirection.SOUTHWEST, FlowDirection.NORTHEAST, FlowDirection.NORTHWEST
        };

        for (int i = 0; i < neighbors.length; i++) {
            int nr = neighbors[i][0];
            int nc = neighbors[i][1];
            if (nr < 0 || nr >= 8 || nc < 0 || nc >= 8) {
                continue;
            }
            if (flowDirections[nr][nc] == expectedDirs[i]) {
                count++;
            }
        }
        return count;
    }

    private static boolean isCellOnRiverPath(RiverRegion region, int row, int col) {
        for (java.util.List<int[]> path : region.getRiverPaths().values()) {
            for (int[] point : path) {
                if (point[0] == row && point[1] == col) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTerminusCell(RiverRegion region, int row, int col) {
        RegionClassification classification = region.getClassification();
        if (classification != RegionClassification.COASTAL
                && classification != RegionClassification.LAKESHORE
                && !region.isBasin()) {
            return false;
        }

        for (java.util.List<int[]> path : region.getRiverPaths().values()) {
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

    private static boolean isSourceCell(RiverRegion region, int row, int col, FlowDirection[][] flowDirections, int inletCount) {
        if (inletCount > 0) {
            return false;
        }

        for (java.util.Map.Entry<PathDirection, java.util.List<int[]>> entry : region.getRiverPaths().entrySet()) {
            java.util.List<int[]> path = entry.getValue();
            if (path.isEmpty()) {
                continue;
            }
            int[] firstPoint = path.get(0);
            if (firstPoint[0] == row && firstPoint[1] == col) {
                boolean isEdge = row == 0 || row == 7 || col == 0 || col == 7;
                if (!isEdge) {
                    return true;
                }
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
            RiverRegion neighbor = getOrCreate(neighborKey, provider, randomState);

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
