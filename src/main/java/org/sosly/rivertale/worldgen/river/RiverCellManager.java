package org.sosly.rivertale.worldgen.river;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class RiverCellManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiverCellManager.class);
    private static final double DENSITY_EPSILON = 0.01;
    private static final int MAX_RECURSION_DEPTH = 1000;

    public static RiverCell createCell(RiverCellKey key, ContinentsDensityProvider provider, long worldSeed) {
        long startTime = System.nanoTime();

        double[][] subcellDensities = provider.sampleSubcellDensities(key.worldX(), key.worldZ(), RiverCellKey.getCellSize());
        double density = provider.getAveragedDensity(key.worldX(), key.worldZ(), RiverCellKey.getCellSize());
        CellClassification classification = classifyCell(key, provider);
        boolean participating = ParticipationCalculator.isParticipating(key, worldSeed);

        RiverCell cell = new RiverCell(key, density, subcellDensities, classification, participating);

        if (participating && classification == CellClassification.LAND) {
            computeFlow(cell, provider, worldSeed);
        }

        long elapsedMs = (System.nanoTime() - startTime) / 1_000_000;
        LOGGER.debug("Cell ({}, {}) created in {}ms [{}]", key.cellX(), key.cellZ(), elapsedMs, classification);

        return cell;
    }

    private static CellClassification classifyCell(RiverCellKey key, ContinentsDensityProvider provider) {
        int cellSize = RiverCellKey.getCellSize();
        double step = cellSize / 7.0;

        boolean hasOcean = false;
        boolean hasLake = false;
        boolean hasLand = false;

        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
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

    private static void computeFlow(RiverCell cell, ContinentsDensityProvider provider, long worldSeed) {
        CellClassification classification = cell.getClassification();

        if (classification == CellClassification.OCEAN || classification == CellClassification.LAKE) {
            cell.setPrimaryOutput(FlowDirection.NONE);
            return;
        }

        if (classification == CellClassification.COASTAL || classification == CellClassification.LAKESHORE) {
            cell.setPrimaryOutput(FlowDirection.NONE);
            return;
        }

        List<NeighborInfo> neighbors = getParticipatingNeighbors(cell.getKey(), provider, worldSeed);
        List<NeighborInfo> lowerNeighbors = new ArrayList<>();

        for (NeighborInfo neighbor : neighbors) {
            if (neighbor.density < cell.getDensity() - DENSITY_EPSILON) {
                lowerNeighbors.add(neighbor);
            }
        }

        if (lowerNeighbors.isEmpty()) {
            cell.setBasin(true);
            cell.setPrimaryOutput(FlowDirection.NONE);
            return;
        }

        lowerNeighbors.sort((a, b) -> Double.compare(a.density, b.density));

        double lowestDensity = lowerNeighbors.get(0).density;
        List<FlowDirection> lowestDirections = new ArrayList<>();
        List<FlowDirection> otherLowerDirections = new ArrayList<>();

        for (NeighborInfo neighbor : lowerNeighbors) {
            if (Math.abs(neighbor.density - lowestDensity) < DENSITY_EPSILON) {
                lowestDirections.add(neighbor.direction);
            } else {
                otherLowerDirections.add(neighbor.direction);
            }
        }

        FlowDirection primary;
        if (lowestDirections.size() == 1) {
            primary = lowestDirections.get(0);
        } else {
            primary = breakTie(cell, lowestDirections, worldSeed);
        }

        cell.setPrimaryOutput(primary);

        Set<FlowDirection> secondaries = new HashSet<>(otherLowerDirections);
        for (FlowDirection direction : lowestDirections) {
            if (direction != primary) {
                secondaries.add(direction);
            }
        }
        cell.setSecondaryOutputs(secondaries);
    }

    private static List<NeighborInfo> getParticipatingNeighbors(RiverCellKey key, ContinentsDensityProvider provider, long worldSeed) {
        List<NeighborInfo> neighbors = new ArrayList<>();
        FlowDirection[] cardinals = {FlowDirection.NORTH, FlowDirection.SOUTH, FlowDirection.EAST, FlowDirection.WEST};

        for (FlowDirection direction : cardinals) {
            RiverCellKey neighborKey = direction.neighbor(key);

            if (!ParticipationCalculator.isParticipating(neighborKey, worldSeed)) {
                continue;
            }

            double neighborDensity = provider.getAveragedDensity(neighborKey.worldX(), neighborKey.worldZ(), RiverCellKey.getCellSize());
            neighbors.add(new NeighborInfo(direction, neighborDensity));
        }

        return neighbors;
    }

    private static FlowDirection breakTie(RiverCell cell, List<FlowDirection> tied, long worldSeed) {
        RiverCellKey key = cell.getKey();
        long seed = key.cellX() * 31L + key.cellZ() * 17L;
        Random rng = new Random(seed ^ worldSeed);
        return tied.get(rng.nextInt(tied.size()));
    }

    public static int getDistanceToTerminus(RiverCell cell, ContinentsDensityProvider provider, long worldSeed) {
        return getDistanceToTerminusRecursive(cell, provider, worldSeed, 0);
    }

    private static int getDistanceToTerminusRecursive(RiverCell cell, ContinentsDensityProvider provider, long worldSeed, int depth) {
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

        if (!cell.isParticipating() || cell.getPrimaryOutput() == FlowDirection.NONE) {
            cell.setDistanceToTerminus(0);
            return 0;
        }

        FlowDirection primaryOutput = cell.getPrimaryOutput();
        RiverCellKey downstreamKey = primaryOutput.neighbor(cell.getKey());
        RiverCell downstreamCell = createCell(downstreamKey, provider, worldSeed);

        int downstreamDistance = getDistanceToTerminusRecursive(downstreamCell, provider, worldSeed, depth + 1);
        int distance = 1 + downstreamDistance;

        cell.setDistanceToTerminus(distance);
        return distance;
    }

    private record NeighborInfo(FlowDirection direction, double density) {}
}
