package org.sosly.rivertale.worldgen.river;

import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sosly.rivertale.config.RiverConfig;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiFunction;

public class RiverCellManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiverCellManager.class);
    private static final int MAX_RECURSION_DEPTH = 1000;

    static RiverCell createCell(RiverCellKey key, DensityProvider provider, RandomState randomState) {
        long startTime = System.nanoTime();

        double[][] subcellDensities = provider.sampleSubcellDensities(key.worldX(), key.worldZ(), RiverCellKey.getCellSize());
        double density = provider.getAveragedDensity(key.worldX(), key.worldZ(), RiverCellKey.getCellSize());
        CellClassification classification = classifyCell(key, provider);
        boolean participating = ParticipationCalculator.isParticipating(key, randomState);

        RiverCell cell = new RiverCell(key, density, subcellDensities, classification, participating);

        if (participating && classification == CellClassification.LAND) {
            computeFlow(cell, provider, randomState);
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        LOGGER.debug("Cell ({}, {}) created in {}ms [{}]", key.cellX(), key.cellZ(), elapsedMs, classification);

        return cell;
    }

    private static CellClassification classifyCell(RiverCellKey key, DensityProvider provider) {
        int cellSize = RiverCellKey.getCellSize();
        double step = cellSize / 8.0;

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
            return CellClassification.COASTAL;
        }
        if (hasOcean) {
            return CellClassification.OCEAN;
        }
        if (hasLake && hasLand) {
            return CellClassification.LAKESHORE;
        }
        if (hasLake) {
            return CellClassification.LAKE;
        }

        return CellClassification.LAND;
    }

    private static void computeFlow(RiverCell cell, DensityProvider provider, RandomState randomState) {
        CellClassification classification = cell.getClassification();

        if (classification == CellClassification.OCEAN || classification == CellClassification.LAKE) {
            cell.setPrimaryOutput(PathDirection.NONE);
            return;
        }

        if (classification == CellClassification.COASTAL || classification == CellClassification.LAKESHORE) {
            cell.setPrimaryOutput(PathDirection.NONE);
            return;
        }

        RiverCellKey key = cell.getKey();
        CellDensityProvider cdp = (CellDensityProvider) provider;

        BiFunction<Integer, Integer, Double> densitySampler = cdp::getDensity;
        BiFunction<Integer, Integer, Double> continentsSampler = cdp::getContinents;
        BiFunction<Integer, Integer, Double> depthSampler = cdp::getDepth;

        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        FlowDirection[][] flowDirections = D8FlowCalculator.computeFlowDirections(key, densitySampler);

        FlowDirection[][][] neighborFlowDirections = new FlowDirection[4][][];
        CellClassification[] neighborClassifications = new CellClassification[4];
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (int i = 0; i < 4; i++) {
            RiverCellKey neighborKey = cardinals[i].neighbor(key);
            neighborFlowDirections[i] = D8FlowCalculator.computeFlowDirections(neighborKey, densitySampler);
            neighborClassifications[i] = D8FlowCalculator.classifyCell(
                neighborKey, continentsSampler, depthSampler, oceanThreshold, lakeThreshold);
        }

        D8FlowResult result = D8PathRefiner.refine(
            key, flowDirections, classification,
            neighborFlowDirections, neighborClassifications,
            densitySampler, continentsSampler, depthSampler,
            oceanThreshold, lakeThreshold);

        cell.setPrimaryOutput(result.primaryOutputDirection());
        cell.setBasin(result.isBasin());

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
        cell.setSecondaryOutputs(secondaries);
    }

    public static RiverCell getOrCreate(RiverCellKey key, DensityProvider provider, RandomState randomState) {
        return RiverCellCache.getOrCompute(key, k -> createCell(k, provider, randomState));
    }

    public static void ensurePaths(RiverCell cell, DensityProvider provider, RandomState randomState) {
        if (!cell.getRiverPaths().isEmpty()) {
            return;
        }

        RiverCellKey key = cell.getKey();
        CellDensityProvider cdp = (CellDensityProvider) provider;

        BiFunction<Integer, Integer, Double> densitySampler = cdp::getDensity;
        BiFunction<Integer, Integer, Double> continentsSampler = cdp::getContinents;
        BiFunction<Integer, Integer, Double> depthSampler = cdp::getDepth;

        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        FlowDirection[][] flowDirections = D8FlowCalculator.computeFlowDirections(key, densitySampler);
        CellClassification classification = cell.getClassification();

        FlowDirection[][][] neighborFlowDirections = new FlowDirection[4][][];
        CellClassification[] neighborClassifications = ensureNeighborsLoaded(key, provider, randomState);
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (int i = 0; i < 4; i++) {
            RiverCellKey neighborKey = cardinals[i].neighbor(key);
            neighborFlowDirections[i] = D8FlowCalculator.computeFlowDirections(neighborKey, densitySampler);
        }

        D8FlowResult result = D8PathRefiner.refine(
            key, flowDirections, classification,
            neighborFlowDirections, neighborClassifications,
            densitySampler, continentsSampler, depthSampler,
            oceanThreshold, lakeThreshold);

        cell.setRiverPaths(result.riverPaths());
    }

    public static CellClassification[] ensureNeighborsLoaded(RiverCellKey key, DensityProvider provider, RandomState randomState) {
        CellClassification[] neighborClassifications = new CellClassification[4];
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (int i = 0; i < cardinals.length; i++) {
            RiverCellKey neighborKey = cardinals[i].neighbor(key);
            RiverCell neighbor = getOrCreate(neighborKey, provider, randomState);
            neighborClassifications[i] = neighbor.getClassification();
        }

        return neighborClassifications;
    }

    public static int getDistanceToTerminus(RiverCell cell, DensityProvider provider, RandomState randomState) {
        return getDistanceToTerminusRecursive(cell, provider, randomState, 0);
    }

    private static int getDistanceToTerminusRecursive(RiverCell cell, DensityProvider provider, RandomState randomState, int depth) {
        if (cell.getDistanceToTerminus() >= 0) {
            return cell.getDistanceToTerminus();
        }

        CellClassification classification = cell.getClassification();
        if (classification == CellClassification.OCEAN
                || classification == CellClassification.COASTAL
                || classification == CellClassification.LAKE
                || classification == CellClassification.LAKESHORE
                || cell.isBasin()) {
            cell.setDistanceToTerminus(0);
            return 0;
        }

        if (depth > MAX_RECURSION_DEPTH) {
            LOGGER.warn("Distance calculation exceeded max depth at cell {}", cell.getKey());
            return depth;
        }

        if (!cell.isParticipating() || cell.getPrimaryOutput() == PathDirection.NONE) {
            cell.setDistanceToTerminus(0);
            return 0;
        }

        PathDirection primaryOutput = cell.getPrimaryOutput();
        RiverCellKey downstreamKey = primaryOutput.neighbor(cell.getKey());
        RiverCell downstreamCell = RiverCellCache.getOrCompute(downstreamKey, k -> createCell(k, provider, randomState));

        int downstreamDistance = getDistanceToTerminusRecursive(downstreamCell, provider, randomState, depth + 1);
        int distance = 1 + downstreamDistance;

        cell.setDistanceToTerminus(distance);
        return distance;
    }

    public static int getUpstreamCount(RiverCellKey key, DensityProvider provider, RandomState randomState) {
        Set<RiverCellKey> visited = new HashSet<>();
        return countUpstream(key, 0, visited, provider, randomState);
    }

    private static int countUpstream(RiverCellKey key, int depth, Set<RiverCellKey> visited, DensityProvider provider, RandomState randomState) {
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
            RiverCellKey neighborKey = dir.neighbor(key);
            RiverCell neighbor = getOrCreate(neighborKey, provider, randomState);

            if (!neighbor.isParticipating()) {
                continue;
            }

            PathDirection neighborOutput = neighbor.getPrimaryOutput();
            if (neighborOutput == null || neighborOutput == PathDirection.NONE) {
                continue;
            }

            RiverCellKey outputTarget = neighborOutput.neighbor(neighborKey);
            if (outputTarget != null && outputTarget.equals(key)) {
                count += 1 + countUpstream(neighborKey, depth + 1, visited, provider, randomState);
            }
        }

        return count;
    }
}
