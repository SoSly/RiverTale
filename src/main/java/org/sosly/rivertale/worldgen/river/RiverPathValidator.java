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

        if (!validatedPaths.isEmpty() && !filteredConfluences.isEmpty()) {
            Map<Long, Set<PathDirection>> confluenceUsage =
                buildConfluenceUsageMap(validatedPaths, filteredConfluences);

            Set<Long> confluenceKeys = new HashSet<>();
            for (int[] cell : filteredConfluences) {
                confluenceKeys.add(cellKey(cell[0], cell[1]));
            }

            Set<PathDirection> tributariesToEvict = new HashSet<>();
            for (Map.Entry<PathDirection, List<int[]>> entry : validatedPaths.entrySet()) {
                PathDirection inputDir = entry.getKey();
                List<int[]> path = entry.getValue();

                int confluenceIdx = findFirstConfluence(path, confluenceKeys);
                if (confluenceIdx < 0) {
                    continue;
                }

                int tributaryLength = computeTributaryLength(
                    key, inputDir, confluenceIdx + 1, provider, randomState);

                if (tributaryLength < getMinimumRiverLength()) {
                    tributariesToEvict.add(inputDir);
                }
            }

            for (PathDirection dir : tributariesToEvict) {
                List<int[]> path = validatedPaths.get(dir);
                int confluenceIdx = findFirstConfluence(path, confluenceKeys);
                evictTributary(validatedPaths, confluenceUsage, dir, confluenceIdx, crossings, strengths);
            }

            filteredConfluences = cleanupConfluences(filteredConfluences, confluenceUsage);
            validCells = collectValidCells(validatedPaths);
            filteredTermini = filterCells(result.terminusCells(), validCells);
        }

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

        if (provider == null) {
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

        if (provider == null) {
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

    private static Map<Long, Set<PathDirection>> buildConfluenceUsageMap(
            Map<PathDirection, List<int[]>> riverPaths,
            List<int[]> confluenceCells) {

        Set<Long> confluenceKeys = new HashSet<>();
        for (int[] cell : confluenceCells) {
            confluenceKeys.add(cellKey(cell[0], cell[1]));
        }

        Map<Long, Set<PathDirection>> usage = new HashMap<>();
        for (Map.Entry<PathDirection, List<int[]>> entry : riverPaths.entrySet()) {
            PathDirection dir = entry.getKey();
            for (int[] cell : entry.getValue()) {
                long key = cellKey(cell[0], cell[1]);
                if (confluenceKeys.contains(key)) {
                    usage.computeIfAbsent(key, k -> new HashSet<>()).add(dir);
                }
            }
        }

        return usage;
    }

    private static int findFirstConfluence(List<int[]> path, Set<Long> confluenceKeys) {
        for (int i = 0; i < path.size(); i++) {
            int[] cell = path.get(i);
            if (confluenceKeys.contains(cellKey(cell[0], cell[1]))) {
                return i;
            }
        }
        return -1;
    }

    private static int computeTributaryLength(
            RiverRegionKey key,
            PathDirection inputDir,
            int localCellCount,
            DensityProvider provider,
            RandomState randomState) {

        int remaining = getMinimumRiverLength() - localCellCount;
        if (remaining <= 0) {
            return localCellCount;
        }

        if (provider == null) {
            return localCellCount;
        }

        int upstream = traceUpstream(key, inputDir, remaining, provider, randomState);
        return localCellCount + upstream;
    }

    private static void evictTributary(
            Map<PathDirection, List<int[]>> validatedPaths,
            Map<Long, Set<PathDirection>> confluenceUsage,
            PathDirection inputDir,
            int confluenceIdx,
            EdgeCrossing[] crossings,
            double[] strengths) {

        List<int[]> path = validatedPaths.get(inputDir);
        if (path == null) {
            return;
        }

        int[] confluenceCell = path.get(confluenceIdx);
        long confluenceKey = cellKey(confluenceCell[0], confluenceCell[1]);
        Set<PathDirection> usage = confluenceUsage.get(confluenceKey);
        if (usage != null) {
            usage.remove(inputDir);
        }

        validatedPaths.remove(inputDir);
        crossings[inputDir.ordinal()] = null;
        strengths[inputDir.ordinal()] = 0;
    }

    private static List<int[]> cleanupConfluences(
            List<int[]> confluenceCells,
            Map<Long, Set<PathDirection>> confluenceUsage) {

        List<int[]> cleaned = new ArrayList<>();
        for (int[] cell : confluenceCells) {
            long key = cellKey(cell[0], cell[1]);
            Set<PathDirection> usage = confluenceUsage.get(key);
            if (usage != null && usage.size() >= 2) {
                cleaned.add(cell);
            }
        }
        return cleaned;
    }
}
