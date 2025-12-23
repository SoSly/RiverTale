package org.sosly.rivertale.worldgen.river;

import net.minecraft.world.level.levelgen.RandomState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

import org.sosly.rivertale.config.RiverConfig;

public class RiverPathValidator {

    private RiverPathValidator() {
    }

    private static final int DEFAULT_MINIMUM_RIVER_LENGTH = 3;

    private static int getMinimumRiverLength() {
        try {
            return RiverConfig.MINIMUM_RIVER_LENGTH.get();
        } catch (IllegalStateException e) {
            return DEFAULT_MINIMUM_RIVER_LENGTH;
        }
    }

    public static D8FlowResult validate(
            D8FlowResult result,
            RiverRegionKey key,
            RegionFeatureType featureType,
            DensityProvider provider,
            RandomState randomState) {

        Map<PathDirection, List<int[]>> validatedPaths = new HashMap<>();
        EdgeCrossing[] crossings = result.crossings().clone();
        double[] strengths = result.crossingStrengths().clone();

        if (result.riverPaths().isEmpty()) {
            boolean hasValidOutput = validateOutputCrossings(
                key, result.primaryOutputDirection(), crossings, strengths,
                featureType, provider, randomState);

            if (!hasValidOutput) {
                return new D8FlowResult(
                    result.flowDirection(),
                    crossings,
                    strengths,
                    PathDirection.NONE,
                    new int[0][],
                    Map.of(),
                    List.of()
                );
            }
            return result;
        }

        for (Map.Entry<PathDirection, List<int[]>> entry : result.riverPaths().entrySet()) {
            PathDirection inputDir = entry.getKey();
            List<int[]> path = entry.getValue();

            if (path.isEmpty()) {
                crossings[inputDir.ordinal()] = null;
                strengths[inputDir.ordinal()] = 0;
                continue;
            }

            int totalLength = computeTotalRiverLength(
                key, inputDir, path.size(), result.primaryOutputDirection(),
                featureType, provider, randomState);

            if (totalLength >= getMinimumRiverLength()) {
                validatedPaths.put(inputDir, path);
                continue;
            }

            crossings[inputDir.ordinal()] = null;
            strengths[inputDir.ordinal()] = 0;
        }

        Set<Long> validCells = collectValidCells(validatedPaths);
        int[][] filteredTermini = filterCells(result.terminusCells(), validCells);
        List<int[]> filteredConfluences = filterCellList(result.confluenceCells(), validCells);

        PathDirection validatedPrimaryOutput = result.primaryOutputDirection();
        if (validatedPaths.isEmpty()) {
            validatedPrimaryOutput = PathDirection.NONE;
            for (int i = 0; i < 4; i++) {
                crossings[i] = null;
                strengths[i] = 0;
            }
        }

        return new D8FlowResult(
            result.flowDirection(),
            crossings,
            strengths,
            validatedPrimaryOutput,
            filteredTermini,
            validatedPaths,
            filteredConfluences
        );
    }

    private static Set<Long> collectValidCells(Map<PathDirection, List<int[]>> paths) {
        Set<Long> cells = new HashSet<>();
        for (List<int[]> path : paths.values()) {
            for (int[] cell : path) {
                cells.add(cellKey(cell[0], cell[1]));
            }
        }
        return cells;
    }

    private static int[][] filterCells(int[][] cells, Set<Long> validCells) {
        List<int[]> filtered = new ArrayList<>();
        for (int[] cell : cells) {
            if (validCells.contains(cellKey(cell[0], cell[1]))) {
                filtered.add(cell);
            }
        }
        return filtered.toArray(new int[0][]);
    }

    private static List<int[]> filterCellList(List<int[]> cells, Set<Long> validCells) {
        List<int[]> filtered = new ArrayList<>();
        for (int[] cell : cells) {
            if (validCells.contains(cellKey(cell[0], cell[1]))) {
                filtered.add(cell);
            }
        }
        return filtered;
    }

    private static long cellKey(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }

    private static boolean validateOutputCrossings(
            RiverRegionKey key,
            PathDirection primaryOutput,
            EdgeCrossing[] crossings,
            double[] strengths,
            RegionFeatureType featureType,
            DensityProvider provider,
            RandomState randomState) {

        if (primaryOutput == null || primaryOutput == PathDirection.NONE) {
            return false;
        }

        int downstream = traceDownstream(key, primaryOutput, featureType, getMinimumRiverLength(), provider, randomState);
        if (downstream >= getMinimumRiverLength()) {
            return true;
        }

        for (int i = 0; i < 4; i++) {
            if (crossings[i] != null && crossings[i].direction() == EdgeCrossing.Direction.OUT) {
                crossings[i] = null;
                strengths[i] = 0;
            }
        }
        return false;
    }

    private static int computeTotalRiverLength(
            RiverRegionKey key,
            PathDirection inputDir,
            int myPathLength,
            PathDirection outputDir,
            RegionFeatureType featureType,
            DensityProvider provider,
            RandomState randomState) {

        int remaining = getMinimumRiverLength() - myPathLength;
        if (remaining <= 0) {
            return myPathLength;
        }

        int upstream = traceUpstream(key, inputDir, remaining, provider, randomState);
        remaining -= upstream;
        if (remaining <= 0) {
            return myPathLength + upstream;
        }

        int downstream = traceDownstream(key, outputDir, featureType, remaining, provider, randomState);

        return myPathLength + upstream + downstream;
    }

    private static int traceUpstream(
            RiverRegionKey key,
            PathDirection inputDir,
            int remaining,
            DensityProvider provider,
            RandomState randomState) {

        if (remaining <= 0) {
            return 0;
        }

        RiverRegionKey neighborKey = inputDir.neighbor(key);
        if (neighborKey == null) {
            return 0;
        }

        RegionFeatureType neighborTerrain = RiverRegionManager.classifyTerrain(neighborKey, provider);
        if (neighborTerrain == RegionFeatureType.BODY) {
            return 0;
        }

        D8FlowResult neighborResult = computeNeighborResult(neighborKey, neighborTerrain, provider, randomState);
        if (neighborResult == null) {
            return 0;
        }

        PathDirection expectedOutput = inputDir.opposite();
        if (neighborResult.primaryOutputDirection() != expectedOutput) {
            return 0;
        }

        int neighborLength = computeNeighborPathLength(neighborResult);
        if (neighborLength == 0) {
            return 0;
        }

        int counted = Math.min(neighborLength, remaining);
        remaining -= counted;

        if (remaining <= 0) {
            return counted;
        }

        PathDirection neighborInput = findLongestInputDirection(neighborResult);
        if (neighborInput == null || neighborInput == PathDirection.NONE) {
            return counted;
        }

        return counted + traceUpstream(neighborKey, neighborInput, remaining, provider, randomState);
    }

    private static int traceDownstream(
            RiverRegionKey key,
            PathDirection outputDir,
            RegionFeatureType featureType,
            int remaining,
            DensityProvider provider,
            RandomState randomState) {

        if (remaining <= 0) {
            return 0;
        }

        if (featureType == RegionFeatureType.SHORE || featureType == RegionFeatureType.BASIN) {
            return 0;
        }

        if (outputDir == null || outputDir == PathDirection.NONE) {
            return 0;
        }

        RiverRegionKey neighborKey = outputDir.neighbor(key);
        if (neighborKey == null) {
            return 0;
        }

        RegionFeatureType neighborTerrain = RiverRegionManager.classifyTerrain(neighborKey, provider);
        if (neighborTerrain == RegionFeatureType.BODY) {
            return 0;
        }

        D8FlowResult neighborResult = computeNeighborResult(neighborKey, neighborTerrain, provider, randomState);
        if (neighborResult == null) {
            return 0;
        }

        PathDirection myInputToNeighbor = outputDir.opposite();
        List<int[]> neighborPath = neighborResult.riverPaths().get(myInputToNeighbor);
        if (neighborPath == null || neighborPath.isEmpty()) {
            return 0;
        }

        int neighborLength = neighborPath.size();
        int counted = Math.min(neighborLength, remaining);
        remaining -= counted;

        if (remaining <= 0) {
            return counted;
        }

        RegionFeatureType neighborFeatureType = neighborTerrain != null ? neighborTerrain : RegionFeatureType.DIVIDE;
        return counted + traceDownstream(
            neighborKey, neighborResult.primaryOutputDirection(),
            neighborFeatureType, remaining, provider, randomState);
    }

    private static D8FlowResult computeNeighborResult(
            RiverRegionKey neighborKey,
            RegionFeatureType neighborTerrain,
            DensityProvider provider,
            RandomState randomState) {

        RegionDensityProvider cdp = (RegionDensityProvider) provider;
        BiFunction<Integer, Integer, Double> densitySampler = cdp::getDensity;
        BiFunction<Integer, Integer, Double> continentsSampler = cdp::getContinents;
        BiFunction<Integer, Integer, Double> depthSampler = cdp::getDepth;

        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        FlowDirection[][] flowDirections = D8FlowCalculator.computeFlowDirections(neighborKey, densitySampler);

        FlowDirection[][][] neighborFlowDirections = new FlowDirection[4][][];
        RegionFeatureType[] neighborFeatureTypes = RiverRegionManager.loadNeighborFeatures(neighborKey, provider);
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (int i = 0; i < 4; i++) {
            RiverRegionKey nnKey = cardinals[i].neighbor(neighborKey);
            neighborFlowDirections[i] = D8FlowCalculator.computeFlowDirections(nnKey, densitySampler);
        }

        RegionFeatureType featureType = neighborTerrain != null ? neighborTerrain : RegionFeatureType.DIVIDE;

        return D8PathRefiner.refine(
            neighborKey, flowDirections, featureType,
            neighborFlowDirections, neighborFeatureTypes,
            densitySampler, continentsSampler, depthSampler,
            oceanThreshold, lakeThreshold);
    }

    private static int computeNeighborPathLength(D8FlowResult result) {
        int maxLength = 0;
        for (List<int[]> path : result.riverPaths().values()) {
            if (path.size() > maxLength) {
                maxLength = path.size();
            }
        }
        return maxLength;
    }

    private static PathDirection findLongestInputDirection(D8FlowResult result) {
        PathDirection longestDir = null;
        int maxLength = 0;

        for (Map.Entry<PathDirection, List<int[]>> entry : result.riverPaths().entrySet()) {
            if (entry.getValue().size() > maxLength) {
                maxLength = entry.getValue().size();
                longestDir = entry.getKey();
            }
        }

        return longestDir;
    }
}
