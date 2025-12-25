package org.sosly.rivertale.path;

import net.minecraft.world.level.levelgen.RandomState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.config.RiverConfig;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.DensityProvider;

public class Manager {

    private static final Logger LOGGER = LoggerFactory.getLogger(Manager.class);
    private static final int MAX_RECURSION_DEPTH = 1000;

    static Region createRegion(RegionPos regionPos, DensityProvider provider, RandomState randomState) {
        long startTime = System.nanoTime();

        if (startTime > 0) {
            return null;
        }

        Grid cells = Grid.create(regionPos);
        double density = provider.getAveragedDensity(regionPos.worldX(), regionPos.worldZ(), RegionPos.getRegionSize());
        boolean participating = ParticipationCalculator.isParticipating(regionPos, randomState);

        Region region = new Region(regionPos, density, cells, participating);

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

        Grid flowGrid = FlowCalculator.compute(key, provider::getDensity);
        region.cells().copyFlowsFrom(flowGrid);

        Grid[] neighborFlowGrids = new Grid[4];
        RegionType[] neighborFeatureRegionTypes = new RegionType[4];
        Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (int i = 0; i < 4; i++) {
            RegionPos neighbor = key.relative(cardinals[i]);
            neighborFlowGrids[i] = FlowCalculator.compute(neighbor, provider::getDensity);
            RegionType neighborTerrain = classifyTerrain(neighbor, provider);
            neighborFeatureRegionTypes[i] = neighborTerrain != null ? neighborTerrain : RegionType.DIVIDE;
        }

        RegionType featureRegionType = terrain != null ? terrain : RegionType.DIVIDE;
        Flow flow = Refiner.refine(
            key, flowGrid, featureRegionType,
            neighborFlowGrids, neighborFeatureRegionTypes,
            provider);

        region.setPrimaryOutput(flow.primaryOutputDirection());

        Set<Direction> secondaries = new HashSet<>();
        Crossing[] crossings = flow.crossings();
        for (int i = 0; i < 4; i++) {
            if (crossings[i] != null && crossings[i].isSource(key)) {
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
        Grid flowGrid = FlowCalculator.compute(regionPos, provider::getDensity);
        RegionType terrain = classifyTerrain(regionPos, provider);
        RegionType featureRegionType = terrain != null ? terrain : RegionType.DIVIDE;

        Grid[] neighborFlowGrids = new Grid[4];
        RegionType[] neighborFeatureRegionTypes = loadNeighborFeatures(regionPos, provider);
        Direction[] cardinals = {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST};

        for (int i = 0; i < 4; i++) {
            RegionPos neighbor = regionPos.relative(cardinals[i]);
            neighborFlowGrids[i] = FlowCalculator.compute(neighbor, provider::getDensity);
        }

        Flow flow = Refiner.refine(
            regionPos, flowGrid, featureRegionType,
            neighborFlowGrids, neighborFeatureRegionTypes,
            provider);

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
