package org.sosly.rivertale.worldgen.river;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class RiverCellManager {

    private static final double DENSITY_EPSILON = 0.01;

    public static RiverCell createCell(RiverCellKey key, ContinentsDensityProvider provider, long worldSeed) {
        double[][] subcellDensities = provider.sampleSubcellDensities(key.worldX(), key.worldZ(), RiverCellKey.getCellSize(key.pass()));
        double density = provider.getAveragedDensity(key.worldX(), key.worldZ(), RiverCellKey.getCellSize(key.pass()));
        CellClassification classification = classifyCell(key, provider);
        boolean participating = ParticipationCalculator.isParticipating(key, worldSeed);

        RiverCell cell = new RiverCell(key, density, subcellDensities, classification, participating);

        if (participating && classification == CellClassification.LAND) {
            computeFlow(cell, provider, worldSeed);
        }

        return cell;
    }

    private static CellClassification classifyCell(RiverCellKey key, ContinentsDensityProvider provider) {
        int cellSize = RiverCellKey.getCellSize(key.pass());
        double step = cellSize / 7.0;

        boolean hasOcean = false;
        boolean hasLand = false;

        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 7; j++) {
                int sampleX = key.worldX() + (int) (i * step);
                int sampleZ = key.worldZ() + (int) (j * step);

                if (provider.isOcean(sampleX, sampleZ)) {
                    hasOcean = true;
                } else {
                    hasLand = true;
                }

                if (hasOcean && hasLand) {
                    return CellClassification.COASTAL;
                }
            }
        }

        if (hasOcean) {
            return CellClassification.OCEAN;
        }

        return CellClassification.LAND;
    }

    private static void computeFlow(RiverCell cell, ContinentsDensityProvider provider, long worldSeed) {
        if (cell.getClassification() == CellClassification.OCEAN) {
            cell.setPrimaryOutput(FlowDirection.NONE);
            return;
        }

        if (cell.getClassification() == CellClassification.COASTAL) {
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

            double neighborDensity = provider.getAveragedDensity(neighborKey.worldX(), neighborKey.worldZ(), RiverCellKey.getCellSize(neighborKey.pass()));
            neighbors.add(new NeighborInfo(direction, neighborDensity));
        }

        return neighbors;
    }

    private static FlowDirection breakTie(RiverCell cell, List<FlowDirection> tied, long worldSeed) {
        RiverCellKey key = cell.getKey();
        long seed = key.cellX() * 31L + key.cellZ() * 17L + key.pass() * 7L;
        Random rng = new Random(seed ^ worldSeed);
        return tied.get(rng.nextInt(tied.size()));
    }

    private record NeighborInfo(FlowDirection direction, double density) {}
}
