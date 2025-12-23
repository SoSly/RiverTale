package org.sosly.rivertale.path;

import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.config.RiverConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.DensityProvider;
import org.sosly.rivertale.terrain.RegionDensityProvider;

public class Manager {

    private static final Logger LOGGER = LoggerFactory.getLogger(Manager.class);
    private static final int MAX_RECURSION_DEPTH = 1000;

    static Region createRegion(RegionPos regionPos, DensityProvider provider, RandomState randomState) {
        long startTime = System.nanoTime();

        double[][] cellDensities = provider.sampleCellDensities(regionPos.worldX(), regionPos.worldZ(), RegionPos.getRegionSize());
        double density = provider.getAveragedDensity(regionPos.worldX(), regionPos.worldZ(), RegionPos.getRegionSize());
        boolean participating = ParticipationCalculator.isParticipating(regionPos, randomState);

        Region region = new Region(regionPos, density, cellDensities, participating);

        RegionType terrain = classifyTerrain(regionPos, provider);
        if (participating && terrain != RegionType.BODY) {
            computeFlow(region, terrain, provider, randomState);
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        LOGGER.debug("Region ({}, {}) created in {}ms [{}]", regionPos.x(), regionPos.z(), elapsedMs, terrain);

        return region;
    }

    public static RegionType classifyTerrain(RegionPos regionPos, DensityProvider provider) {
        int regionSize = RegionPos.getRegionSize();
        double step = regionSize / 8.0;

        boolean hasWater = false;
        boolean hasLand = false;

        for (int i = 0; i < 8 && (!hasWater || !hasLand); i++) {
            for (int j = 0; j < 8 && (!hasWater || !hasLand); j++) {
                int sampleX = regionPos.worldX() + (int) ((i + 0.5) * step);
                int sampleZ = regionPos.worldZ() + (int) ((j + 0.5) * step);

                if (provider.isOcean(sampleX, sampleZ) || provider.isLake(sampleX, sampleZ)) {
                    hasWater = true;
                } else {
                    hasLand = true;
                }
            }
        }

        if (hasWater && hasLand) {
            return RegionType.SHORE;
        }
        if (hasWater) {
            return RegionType.BODY;
        }

        return null;
    }

    private static void computeFlow(Region region, RegionType terrain, DensityProvider provider, RandomState randomState) {
        if (terrain == RegionType.BODY) {
            region.setPrimaryOutput(Direction.NONE);
            return;
        }

        if (terrain == RegionType.SHORE) {
            region.setPrimaryOutput(Direction.NONE);
            return;
        }

        RegionPos key = region.pos();
        RegionDensityProvider cdp = (RegionDensityProvider) provider;

        BiFunction<Integer, Integer, Double> densitySampler = cdp::getDensity;
        BiFunction<Integer, Integer, Double> continentsSampler = cdp::getContinents;
        BiFunction<Integer, Integer, Double> depthSampler = cdp::getDepth;

        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        Direction[][] flowDirections = FlowCalculator.compute(key, densitySampler);

        Direction[][][] neighborFlowDirections = new Direction[4][][];
        RegionType[] neighborFeatureRegionTypes = new RegionType[4];
        Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (int i = 0; i < 4; i++) {
            RegionPos neighbor = key.relative(cardinals[i]);
            neighborFlowDirections[i] = FlowCalculator.compute(neighbor, densitySampler);
            RegionType neighborTerrain = classifyTerrain(neighbor, provider);
            neighborFeatureRegionTypes[i] = neighborTerrain != null ? neighborTerrain : RegionType.DIVIDE;
        }

        RegionType featureRegionType = terrain != null ? terrain : RegionType.DIVIDE;
        Flow flow = Refiner.refine(
            key, flowDirections, featureRegionType,
            neighborFlowDirections, neighborFeatureRegionTypes,
            densitySampler, continentsSampler, depthSampler,
            oceanThreshold, lakeThreshold);

        region.setPrimaryOutput(flow.primaryOutputDirection());

        Set<Direction> secondaries = new HashSet<>();
        Crossing[] crossings = flow.crossings();
        for (int i = 0; i < 4; i++) {
            if (crossings[i] != null && crossings[i].direction() == Crossing.Direction.OUT) {
                Direction dir = cardinals[i];
                if (dir != flow.primaryOutputDirection()) {
                    secondaries.add(dir);
                }
            }
        }
        region.setSecondaryOutputs(secondaries);
    }

    public static Region createRegionFor(RegionPos regionPos, DensityProvider provider, RandomState randomState) {
        return createRegion(regionPos, provider, randomState);
    }

    public static Map<Direction, List<CellPos>> computePaths(Region region, DensityProvider provider, RandomState randomState) {
        return computeFlowResult(region.pos(), provider, randomState).riverPaths();
    }

    public static Flow computeFlowResult(RegionPos regionPos, DensityProvider provider, RandomState randomState) {
        RegionDensityProvider cdp = (RegionDensityProvider) provider;

        BiFunction<Integer, Integer, Double> densitySampler = cdp::getDensity;
        BiFunction<Integer, Integer, Double> continentsSampler = cdp::getContinents;
        BiFunction<Integer, Integer, Double> depthSampler = cdp::getDepth;

        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        Direction[][] flowDirections = FlowCalculator.compute(regionPos, densitySampler);
        RegionType terrain = classifyTerrain(regionPos, provider);
        RegionType featureRegionType = terrain != null ? terrain : RegionType.DIVIDE;

        Direction[][][] neighborFlowDirections = new Direction[4][][];
        RegionType[] neighborFeatureRegionTypes = loadNeighborFeatures(regionPos, provider);
        Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (int i = 0; i < 4; i++) {
            RegionPos neighbor = regionPos.relative(cardinals[i]);
            neighborFlowDirections[i] = FlowCalculator.compute(neighbor, densitySampler);
        }

        Flow flow = Refiner.refine(
            regionPos, flowDirections, featureRegionType,
            neighborFlowDirections, neighborFeatureRegionTypes,
            densitySampler, continentsSampler, depthSampler,
            oceanThreshold, lakeThreshold);

        return Validator.validate(flow, regionPos, featureRegionType, provider, randomState);
    }

    public static RegionType[] loadNeighborFeatures(RegionPos regionPos, DensityProvider provider) {
        RegionType[] neighborFeatureRegionTypes = new RegionType[4];
        Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (int i = 0; i < cardinals.length; i++) {
            RegionPos neighbor = regionPos.relative(cardinals[i]);
            RegionType terrain = classifyTerrain(neighbor, provider);
            neighborFeatureRegionTypes[i] = terrain != null ? terrain : RegionType.DIVIDE;
        }

        return neighborFeatureRegionTypes;
    }

    public static int getDistanceToTerminus(Region region, DensityProvider provider, RandomState randomState) {
        return getDistanceToTerminusRecursive(region, provider, randomState, 0);
    }

    private static int getDistanceToTerminusRecursive(Region region, DensityProvider provider, RandomState randomState, int depth) {
        RegionType featureRegionType = getRegionFeatureType(region, provider, randomState);
        if (featureRegionType == RegionType.BODY
                || featureRegionType == RegionType.SHORE
                || featureRegionType == RegionType.BASIN) {
            return 0;
        }

        if (depth > MAX_RECURSION_DEPTH) {
            LOGGER.warn("Distance calculation exceeded max depth at region {}", region.pos());
            return depth;
        }

        if (!region.isParticipating() || region.getPrimaryOutput() == Direction.NONE) {
            return 0;
        }

        Direction primaryOutput = region.getPrimaryOutput();
        RegionPos downstreamPos = region.pos().relative(primaryOutput);
        Region downstream = createRegion(downstreamPos, provider, randomState);

        int downstreamDistance = getDistanceToTerminusRecursive(downstream, provider, randomState, depth + 1);
        return 1 + downstreamDistance;
    }

    public static int getUpstreamCount(RegionPos regionPos, DensityProvider provider, RandomState randomState) {
        Set<RegionPos> visited = new HashSet<>();
        return countUpstream(regionPos, 0, visited, provider, randomState);
    }

    public static RegionType getRegionFeatureType(Region region, DensityProvider provider, RandomState randomState) {
        RegionType terrain = classifyTerrain(region.pos(), provider);
        if (terrain != null) {
            return terrain;
        }

        if (!region.isParticipating()) {
            return RegionType.BARREN;
        }

        if (region.getPrimaryOutput() == Direction.NONE && region.getSecondaryOutputs().isEmpty()) {
            return RegionType.BASIN;
        }

        int upstreamCount = getUpstreamCount(region.pos(), provider, randomState);
        return upstreamCount == 0 ? RegionType.DIVIDE : RegionType.FLUVIAL;
    }

    public static CellType getCellFeatureType(Region region, int row, int col, Map<Direction, List<CellPos>> paths, DensityProvider provider) {
        if (!isCellOnRiverPath(paths, row, col)) {
            return null;
        }

        int inletCount = countInlets(paths, row, col);

        if (isTerminusCell(region, paths, row, col, provider)) {
            return CellType.TERMINUS;
        }
        if (isSourceCell(paths, row, col, inletCount)) {
            return CellType.SOURCE;
        }
        if (inletCount >= 2) {
            return CellType.JUNCTION;
        }
        return CellType.COURSE;
    }

    private static int countInlets(Map<Direction, List<CellPos>> paths, int row, int col) {
        Set<CellPos> uniqueInlets = new HashSet<>();
        for (List<CellPos> path : paths.values()) {
            for (int i = 1; i < path.size(); i++) {
                CellPos current = path.get(i);
                if (current.row() == row && current.col() == col) {
                    CellPos previous = path.get(i - 1);
                    uniqueInlets.add(previous);
                }
            }
        }
        return uniqueInlets.size();
    }

    private static boolean isCellOnRiverPath(Map<Direction, List<CellPos>> paths, int row, int col) {
        for (List<CellPos> path : paths.values()) {
            for (CellPos point : path) {
                if (point.row() == row && point.col() == col) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTerminusCell(Region region, Map<Direction, List<CellPos>> paths, int row, int col, DensityProvider provider) {
        RegionType terrain = classifyTerrain(region.pos(), provider);
        boolean isShore = terrain == RegionType.SHORE;
        boolean isBasin = region.getPrimaryOutput() == Direction.NONE
            && region.getSecondaryOutputs().isEmpty();

        if (!isShore && !isBasin) {
            return false;
        }

        for (List<CellPos> path : paths.values()) {
            if (path.isEmpty()) {
                continue;
            }
            CellPos lastPoint = path.get(path.size() - 1);
            if (lastPoint.row() == row && lastPoint.col() == col) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSourceCell(Map<Direction, List<CellPos>> paths, int row, int col, int inletCount) {
        if (inletCount > 0) {
            return false;
        }

        for (Map.Entry<Direction, List<CellPos>> entry : paths.entrySet()) {
            List<CellPos> path = entry.getValue();
            if (path.isEmpty()) {
                continue;
            }
            CellPos firstPoint = path.get(0);
            if (firstPoint.row() == row && firstPoint.col() == col) {
                return true;
            }
        }
        return false;
    }

    private static int countUpstream(RegionPos regionPos, int depth, Set<RegionPos> visited, DensityProvider provider, RandomState randomState) {
        if (depth > RiverConfig.UPSTREAM_DEPTH_LIMIT.get()) {
            return 0;
        }
        if (visited.contains(regionPos)) {
            return 0;
        }
        visited.add(regionPos);

        int count = 0;
        Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (Direction dir : cardinals) {
            RegionPos neighborPos = regionPos.relative(dir);
            Region neighbor = createRegion(neighborPos, provider, randomState);

            if (!neighbor.isParticipating()) {
                continue;
            }

            Direction neighborOutput = neighbor.getPrimaryOutput();
            if (neighborOutput == null || neighborOutput == Direction.NONE) {
                continue;
            }

            RegionPos output = neighborPos.relative(neighborOutput);
            if (output.equals(regionPos)) {
                count += 1 + countUpstream(neighborPos, depth + 1, visited, provider, randomState);
            }
        }

        return count;
    }
}
