package org.sosly.rivertale.path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.DensityProvider;
import org.sosly.rivertale.terrain.RegionDensityProvider;

public class Validator {

    private Validator() {
    }

    private static final int DEFAULT_MINIMUM_RIVER_LENGTH = 3;

    private static int getMinimumRiverLength() {
        try {
            return RiverConfig.MINIMUM_RIVER_LENGTH.get();
        } catch (IllegalStateException e) {
            return DEFAULT_MINIMUM_RIVER_LENGTH;
        }
    }

    public static Flow validate(
            Flow flow,
            RegionPos regionPos,
            RegionType featureRegionType,
            DensityProvider provider,
            RandomState randomState) {

        Map<Direction, List<CellPos>> validatedPaths = new HashMap<>();
        Crossing[] crossings = flow.crossings().clone();
        double[] strengths = flow.crossingStrengths().clone();

        if (flow.riverPaths().isEmpty()) {
            boolean hasValidOutput = validateOutputCrossings(
                regionPos, flow.primaryOutputDirection(), crossings, strengths,
                    featureRegionType, provider, randomState);

            if (!hasValidOutput) {
                return new Flow(
                    flow.flowDirection(),
                    crossings,
                    strengths,
                    Direction.NONE,
                    List.of(),
                    Map.of(),
                    List.of()
                );
            }
            return flow;
        }

        for (Map.Entry<Direction, List<CellPos>> entry : flow.riverPaths().entrySet()) {
            Direction inputDir = entry.getKey();
            List<CellPos> path = entry.getValue();

            if (path.isEmpty()) {
                crossings[inputDir.ordinal()] = null;
                strengths[inputDir.ordinal()] = 0;
                continue;
            }

            int totalLength = computeTotalRiverLength(
                regionPos, inputDir, path.size(), flow.primaryOutputDirection(),
                    featureRegionType, provider, randomState);

            if (totalLength >= getMinimumRiverLength()) {
                validatedPaths.put(inputDir, path);
                continue;
            }

            crossings[inputDir.ordinal()] = null;
            strengths[inputDir.ordinal()] = 0;
        }

        Set<CellPos> validCells = collectValidCells(validatedPaths);
        List<CellPos> filteredTermini = filterCells(flow.terminusCells(), validCells);
        List<CellPos> filteredConfluences = filterCells(flow.confluenceCells(), validCells);

        if (!validatedPaths.isEmpty() && !filteredConfluences.isEmpty()) {
            Map<CellPos, Set<Direction>> confluenceUsage =
                buildConfluenceUsageMap(validatedPaths, filteredConfluences);

            Set<CellPos> confluenceSet = new HashSet<>(filteredConfluences);

            Set<Direction> tributariesToEvict = new HashSet<>();
            for (Map.Entry<Direction, List<CellPos>> entry : validatedPaths.entrySet()) {
                Direction inputDir = entry.getKey();
                List<CellPos> path = entry.getValue();

                int confluenceIdx = findFirstConfluence(path, confluenceSet);
                if (confluenceIdx < 0) {
                    continue;
                }

                int tributaryLength = computeTributaryLength(
                    regionPos, inputDir, confluenceIdx + 1, provider, randomState);

                if (tributaryLength < getMinimumRiverLength()) {
                    tributariesToEvict.add(inputDir);
                }
            }

            for (Direction dir : tributariesToEvict) {
                List<CellPos> path = validatedPaths.get(dir);
                int confluenceIdx = findFirstConfluence(path, confluenceSet);
                evictTributary(validatedPaths, confluenceUsage, dir, confluenceIdx, crossings, strengths);
            }

            filteredConfluences = cleanupConfluences(filteredConfluences, confluenceUsage);
            validCells = collectValidCells(validatedPaths);
            filteredTermini = filterCells(flow.terminusCells(), validCells);
        }

        Direction validatedPrimaryOutput = flow.primaryOutputDirection();
        if (validatedPaths.isEmpty()) {
            validatedPrimaryOutput = Direction.NONE;
            for (int i = 0; i < 4; i++) {
                crossings[i] = null;
                strengths[i] = 0;
            }
        }

        return new Flow(
            flow.flowDirection(),
            crossings,
            strengths,
            validatedPrimaryOutput,
            filteredTermini,
            validatedPaths,
            filteredConfluences
        );
    }

    private static Set<CellPos> collectValidCells(Map<Direction, List<CellPos>> paths) {
        Set<CellPos> cells = new HashSet<>();
        for (List<CellPos> path : paths.values()) {
            cells.addAll(path);
        }
        return cells;
    }

    private static List<CellPos> filterCells(List<CellPos> cells, Set<CellPos> validCells) {
        List<CellPos> filtered = new ArrayList<>();
        for (CellPos cell : cells) {
            if (validCells.contains(cell)) {
                filtered.add(cell);
            }
        }
        return filtered;
    }

    private static boolean validateOutputCrossings(
            RegionPos regionPos,
            Direction primaryOutput,
            Crossing[] crossings,
            double[] strengths,
            RegionType featureRegionType,
            DensityProvider provider,
            RandomState randomState) {

        if (primaryOutput == null || primaryOutput == Direction.NONE) {
            return false;
        }

        int downstream = traceDownstream(regionPos, primaryOutput, featureRegionType, getMinimumRiverLength(), provider, randomState);
        if (downstream >= getMinimumRiverLength()) {
            return true;
        }

        for (int i = 0; i < 4; i++) {
            if (crossings[i] != null && crossings[i].direction() == Crossing.Direction.OUT) {
                crossings[i] = null;
                strengths[i] = 0;
            }
        }
        return false;
    }

    private static int computeTotalRiverLength(
            RegionPos regionPos,
            Direction inputDir,
            int myPathLength,
            Direction outputDir,
            RegionType featureRegionType,
            DensityProvider provider,
            RandomState randomState) {

        int remaining = getMinimumRiverLength() - myPathLength;
        if (remaining <= 0) {
            return myPathLength;
        }

        int upstream = traceUpstream(regionPos, inputDir, remaining, provider, randomState);
        remaining -= upstream;
        if (remaining <= 0) {
            return myPathLength + upstream;
        }

        int downstream = traceDownstream(regionPos, outputDir, featureRegionType, remaining, provider, randomState);

        return myPathLength + upstream + downstream;
    }

    private static int traceUpstream(
            RegionPos pos,
            Direction dir,
            int remaining,
            DensityProvider provider,
            RandomState random) {

        if (remaining <= 0) {
            return 0;
        }

        if (provider == null) {
            return 0;
        }

        RegionPos neighbor = pos.relative(dir);
        RegionType terrain = Manager.classifyTerrain(neighbor, provider);
        Optional<Flow> flow = computeNeighborFlow(neighbor, terrain, provider, random);
        if (flow.isEmpty()) {
            return 0;
        }

        Direction expectedOutput = dir.opposite();
        if (flow.get().primaryOutputDirection() != expectedOutput) {
            return 0;
        }

        int neighborLength = computeNeighborPathLength(flow.get());
        if (neighborLength == 0) {
            return 0;
        }

        int counted = Math.min(neighborLength, remaining);
        remaining -= counted;

        if (remaining <= 0) {
            return counted;
        }

        Direction neighborInput = findLongestInputDirection(flow.get());
        if (neighborInput == null || neighborInput == Direction.NONE) {
            return counted;
        }

        return counted + traceUpstream(neighbor, neighborInput, remaining, provider, random);
    }

    private static int traceDownstream(
            RegionPos pos,
            Direction dir,
            RegionType regionType,
            int remaining,
            DensityProvider provider,
            RandomState random) {

        if (remaining <= 0) {
            return 0;
        }

        if (provider == null) {
            return 0;
        }

        if (regionType == RegionType.SHORE || regionType == RegionType.BASIN) {
            return 0;
        }

        if (dir == null || dir == Direction.NONE) {
            return 0;
        }

        RegionPos neighbor = pos.relative(dir);
        RegionType terrain = Manager.classifyTerrain(neighbor, provider);
        Optional<Flow> flow = computeNeighborFlow(neighbor, terrain, provider, random);
        if (flow.isEmpty()) {
            return 0;
        }

        List<CellPos> neighborPath = flow.get().riverPaths().get(dir.opposite());
        if (neighborPath == null || neighborPath.isEmpty()) {
            return 0;
        }

        int neighborLength = neighborPath.size();
        int counted = Math.min(neighborLength, remaining);
        remaining -= counted;

        if (remaining <= 0) {
            return counted;
        }

        RegionType neighborFeatureRegionType = terrain != null ? terrain : RegionType.DIVIDE;
        return counted + traceDownstream(
            neighbor, flow.get().primaryOutputDirection(),
                neighborFeatureRegionType, remaining, provider, random);
    }

    private static Optional<Flow> computeNeighborFlow(
            RegionPos pos,
            RegionType terrain,
            DensityProvider provider,
            RandomState random) {

        if (terrain == RegionType.BODY) {
            return Optional.empty();
        }

        RegionDensityProvider cdp = (RegionDensityProvider) provider;
        BiFunction<Integer, Integer, Double> densitySampler = cdp::getDensity;
        BiFunction<Integer, Integer, Double> continentsSampler = cdp::getContinents;
        BiFunction<Integer, Integer, Double> depthSampler = cdp::getDepth;

        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        Direction[][] flowDirections = FlowCalculator.compute(pos, densitySampler);

        Direction[][][] neighborFlowDirections = new Direction[4][][];
        RegionType[] neighborFeatureRegionTypes = Manager.loadNeighborFeatures(pos, provider);
        Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (int i = 0; i < 4; i++) {
            RegionPos nnKey = cardinals[i].neighbor(pos);
            neighborFlowDirections[i] = FlowCalculator.compute(nnKey, densitySampler);
        }

        RegionType featureRegionType = terrain != null ? terrain : RegionType.DIVIDE;

        return Optional.of(Refiner.refine(
            pos, flowDirections, featureRegionType,
            neighborFlowDirections, neighborFeatureRegionTypes,
            densitySampler, continentsSampler, depthSampler,
            oceanThreshold, lakeThreshold));
    }

    private static int computeNeighborPathLength(Flow flow) {
        int maxLength = 0;
        for (List<CellPos> path : flow.riverPaths().values()) {
            if (path.size() > maxLength) {
                maxLength = path.size();
            }
        }
        return maxLength;
    }

    private static Direction findLongestInputDirection(Flow flow) {
        Direction longestDir = null;
        int maxLength = 0;

        for (Map.Entry<Direction, List<CellPos>> entry : flow.riverPaths().entrySet()) {
            if (entry.getValue().size() > maxLength) {
                maxLength = entry.getValue().size();
                longestDir = entry.getKey();
            }
        }

        return longestDir;
    }

    private static Map<CellPos, Set<Direction>> buildConfluenceUsageMap(
            Map<Direction, List<CellPos>> riverPaths,
            List<CellPos> confluenceCells) {

        Set<CellPos> confluenceSet = new HashSet<>(confluenceCells);

        Map<CellPos, Set<Direction>> usage = new HashMap<>();
        for (Map.Entry<Direction, List<CellPos>> entry : riverPaths.entrySet()) {
            Direction dir = entry.getKey();
            for (CellPos cell : entry.getValue()) {
                if (confluenceSet.contains(cell)) {
                    usage.computeIfAbsent(cell, k -> new HashSet<>()).add(dir);
                }
            }
        }

        return usage;
    }

    private static int findFirstConfluence(List<CellPos> path, Set<CellPos> confluenceSet) {
        for (int i = 0; i < path.size(); i++) {
            if (confluenceSet.contains(path.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private static int computeTributaryLength(
            RegionPos regionPos,
            Direction inputDir,
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

        int upstream = traceUpstream(regionPos, inputDir, remaining, provider, randomState);
        return localCellCount + upstream;
    }

    private static void evictTributary(
            Map<Direction, List<CellPos>> validatedPaths,
            Map<CellPos, Set<Direction>> confluenceUsage,
            Direction inputDir,
            int confluenceIdx,
            Crossing[] crossings,
            double[] strengths) {

        List<CellPos> path = validatedPaths.get(inputDir);
        if (path == null) {
            return;
        }

        CellPos confluenceCell = path.get(confluenceIdx);
        Set<Direction> usage = confluenceUsage.get(confluenceCell);
        if (usage != null) {
            usage.remove(inputDir);
        }

        validatedPaths.remove(inputDir);
        crossings[inputDir.ordinal()] = null;
        strengths[inputDir.ordinal()] = 0;
    }

    private static List<CellPos> cleanupConfluences(
            List<CellPos> confluenceCells,
            Map<CellPos, Set<Direction>> confluenceUsage) {

        List<CellPos> cleaned = new ArrayList<>();
        for (CellPos cell : confluenceCells) {
            Set<Direction> usage = confluenceUsage.get(cell);
            if (usage != null && usage.size() >= 2) {
                cleaned.add(cell);
            }
        }
        return cleaned;
    }
}
