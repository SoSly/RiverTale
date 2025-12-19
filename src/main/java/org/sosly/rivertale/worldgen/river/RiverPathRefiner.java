package org.sosly.rivertale.worldgen.river;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RiverPathRefiner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiverPathRefiner.class);
    private static final double EXIT_WEIGHT_FACTOR = 0.25;
    private static final double TERMINUS_WEIGHT_FACTOR = 0.5;
    private static final int SUBCELL_SIZE = 7;
    private static final int CENTER_SUBCELL = 3;

    public static void refineRiverPath(RiverCell cell, DensityProvider provider,
                                       RiverCellSavedData savedData, long worldSeed) {
        CellClassification classification = cell.getClassification();

        if (classification == CellClassification.OCEAN || classification == CellClassification.LAKE) {
            return;
        }

        if (!cell.isParticipating()) {
            return;
        }

        Map<FlowDirection, List<int[]>> paths = new HashMap<>();
        Set<FlowDirection> inputs = detectInputs(cell, savedData);

        if (classification == CellClassification.COASTAL || classification == CellClassification.LAKESHORE) {
            int[] biasTarget = findWaterSubcell(cell.getKey(), provider);
            LOGGER.info("Cell ({}, {}) classification={} water subcell at [{}, {}]",
                cell.getKey().cellX(), cell.getKey().cellZ(), classification, biasTarget[0], biasTarget[1]);
            double[][] weightedCosts = applyExitWeighting(cell.getSubcellDensities(), biasTarget, TERMINUS_WEIGHT_FACTOR);

            List<int[]> primaryPath = null;
            Set<String> allPathCells = new HashSet<>();

            // TODO: Replace cardinal priority with upstream density ordering when available
            List<FlowDirection> sortedInputs = sortByCardinalPriority(inputs);

            for (FlowDirection input : sortedInputs) {
                int[] entry = getSubcellAtEdge(input);

                if (primaryPath == null) {
                    primaryPath = findPathToTerminus(entry, weightedCosts, provider, cell.getKey());
                    if (primaryPath != null && !primaryPath.isEmpty()) {
                        for (int[] point : primaryPath) {
                            allPathCells.add(point[0] + "," + point[1]);
                        }
                        paths.put(input, primaryPath);
                    }
                } else {
                    List<int[]> path = findPathToTerminusOrExistingPath(entry, weightedCosts, primaryPath, allPathCells, provider, cell.getKey());
                    if (path != null && !path.isEmpty()) {
                        for (int[] point : path) {
                            allPathCells.add(point[0] + "," + point[1]);
                        }
                        paths.put(input, path);
                    }
                }
            }

            for (FlowDirection secondary : cell.getSecondaryOutputs()) {
                int[] center = new int[]{CENTER_SUBCELL, CENTER_SUBCELL};
                List<int[]> path;
                if (primaryPath == null) {
                    path = findPathToTerminus(center, weightedCosts, provider, cell.getKey());
                    if (path != null && !path.isEmpty()) {
                        primaryPath = path;
                        for (int[] point : primaryPath) {
                            allPathCells.add(point[0] + "," + point[1]);
                        }
                    }
                } else {
                    path = findPathToTerminusOrExistingPath(center, weightedCosts, primaryPath, allPathCells, provider, cell.getKey());
                }
                if (path != null && !path.isEmpty()) {
                    for (int[] point : path) {
                        allPathCells.add(point[0] + "," + point[1]);
                    }
                    paths.put(secondary, path);
                }
            }

            cell.setRiverPaths(paths);
            return;
        }

        if (classification == CellClassification.LAND) {
            FlowDirection primaryOutput = cell.getPrimaryOutput();

            if (inputs.isEmpty() && primaryOutput != FlowDirection.NONE) {
                int[] sourcePoint = getSourcePointNearEdge(primaryOutput);
                int[] exit = getSubcellAtEdge(primaryOutput);
                double[][] weightedCosts = applyExitWeighting(cell.getSubcellDensities(), exit);
                List<int[]> path = findPath(sourcePoint, exit, weightedCosts, cell.getSubcellDensities());
                if (path != null && !path.isEmpty()) {
                    paths.put(primaryOutput, path);
                }
            } else if (!inputs.isEmpty() && primaryOutput != FlowDirection.NONE) {
                List<int[]> primaryPath = null;
                Set<String> allPathCells = new HashSet<>();
                int[] exit = getSubcellAtEdge(primaryOutput);
                double[][] weightedCosts = applyExitWeighting(cell.getSubcellDensities(), exit);

                // TODO: Replace cardinal priority with upstream density ordering when available
                List<FlowDirection> sortedInputs = sortByCardinalPriority(inputs);

                for (FlowDirection input : sortedInputs) {
                    int[] entry = getSubcellAtEdge(input);

                    if (primaryPath == null) {
                        primaryPath = findPath(entry, exit, weightedCosts, cell.getSubcellDensities());
                        if (primaryPath != null && !primaryPath.isEmpty()) {
                            for (int[] point : primaryPath) {
                                allPathCells.add(point[0] + "," + point[1]);
                            }
                            paths.put(input, primaryPath);
                        }
                    } else {
                        List<int[]> path = findPathToTerminusOrExistingPath(entry, weightedCosts, primaryPath, allPathCells, null, null);
                        if (path != null && !path.isEmpty()) {
                            for (int[] point : path) {
                                allPathCells.add(point[0] + "," + point[1]);
                            }
                            paths.put(input, path);
                        }
                    }
                }
            } else if (!inputs.isEmpty() && cell.isBasin()) {
                List<int[]> primaryPath = null;
                Set<String> allPathCells = new HashSet<>();
                int[] center = new int[]{CENTER_SUBCELL, CENTER_SUBCELL};

                // TODO: Replace cardinal priority with upstream density ordering when available
                List<FlowDirection> sortedInputs = sortByCardinalPriority(inputs);

                for (FlowDirection input : sortedInputs) {
                    int[] entry = getSubcellAtEdge(input);

                    if (primaryPath == null) {
                        primaryPath = findPath(entry, center, cell.getSubcellDensities(), cell.getSubcellDensities());
                        if (primaryPath != null && !primaryPath.isEmpty()) {
                            for (int[] point : primaryPath) {
                                allPathCells.add(point[0] + "," + point[1]);
                            }
                            paths.put(input, primaryPath);
                        }
                    } else {
                        List<int[]> path = findPathToTerminusOrExistingPath(entry, cell.getSubcellDensities(), primaryPath, allPathCells, null, null);
                        if (path != null && !path.isEmpty()) {
                            for (int[] point : path) {
                                allPathCells.add(point[0] + "," + point[1]);
                            }
                            paths.put(input, path);
                        }
                    }
                }
            }

            for (FlowDirection secondary : cell.getSecondaryOutputs()) {
                int[] exit = getSubcellAtEdge(secondary);
                int[] sourcePoint = getSourcePointNearEdge(secondary);
                double[][] weightedCosts = applyExitWeighting(cell.getSubcellDensities(), exit);
                List<int[]> path = findPath(sourcePoint, exit, weightedCosts, cell.getSubcellDensities());
                if (path != null && !path.isEmpty()) {
                    paths.put(secondary, path);
                }
            }

            cell.setRiverPaths(paths);
        }
    }

    private static Set<FlowDirection> detectInputs(RiverCell cell, RiverCellSavedData savedData) {
        Set<FlowDirection> inputs = new HashSet<>();
        FlowDirection[] cardinals = {FlowDirection.NORTH, FlowDirection.SOUTH, FlowDirection.EAST, FlowDirection.WEST};

        LOGGER.info("detectInputs for cell ({}, {})", cell.getKey().cellX(), cell.getKey().cellZ());

        for (FlowDirection direction : cardinals) {
            RiverCellKey neighborKey = direction.neighbor(cell.getKey());
            RiverCell neighbor = savedData.getIfPresent(neighborKey);

            if (neighbor == null) {
                LOGGER.info("  {} neighbor ({}, {}) is NULL", direction, neighborKey.cellX(), neighborKey.cellZ());
                continue;
            }

            FlowDirection outputTowardUs = direction.opposite();
            LOGGER.info("  {} neighbor ({}, {}): primaryOutput={}, secondaries={}, outputTowardUs={}",
                direction, neighborKey.cellX(), neighborKey.cellZ(),
                neighbor.getPrimaryOutput(), neighbor.getSecondaryOutputs(), outputTowardUs);

            if (neighbor.getPrimaryOutput() == outputTowardUs) {
                LOGGER.info("    -> Adding {} as input (primary match)", direction);
                inputs.add(direction);
            } else if (neighbor.getSecondaryOutputs().contains(outputTowardUs)) {
                LOGGER.info("    -> Adding {} as input (secondary match)", direction);
                inputs.add(direction);
            }
        }

        LOGGER.info("  Final inputs: {}", inputs);
        return inputs;
    }

    private static List<int[]> findPath(int[] entry, int[] exit, double[][] costs, double[][] rawDensities) {
        List<int[]> path = new ArrayList<>();
        boolean[][] visited = new boolean[SUBCELL_SIZE][SUBCELL_SIZE];
        int[] current = entry.clone();

        path.add(current.clone());
        visited[current[0]][current[1]] = true;

        while (!isAdjacent(current, exit)) {
            List<int[]> unvisitedNeighbors = getUnvisitedNeighbors(current, visited);

            if (unvisitedNeighbors.isEmpty()) {
                if (path.size() <= 1) {
                    return path;
                }
                path.remove(path.size() - 1);
                if (path.isEmpty()) {
                    return path;
                }
                current = path.get(path.size() - 1);
                continue;
            }

            List<int[]> lowerNeighbors = getLowerCostNeighbors(current, unvisitedNeighbors, costs);

            if (!lowerNeighbors.isEmpty()) {
                current = findLowestCost(lowerNeighbors, costs);
            } else {
                List<int[]> closerNeighbors = getCloserNeighbors(current, unvisitedNeighbors, exit);
                if (!closerNeighbors.isEmpty()) {
                    current = findLowestCost(closerNeighbors, costs);
                } else {
                    if (path.size() <= 1) {
                        return path;
                    }
                    path.remove(path.size() - 1);
                    if (path.isEmpty()) {
                        return path;
                    }
                    current = path.get(path.size() - 1);
                    continue;
                }
            }

            path.add(current.clone());
            visited[current[0]][current[1]] = true;
        }

        path.add(exit.clone());
        return path;
    }

    private static List<int[]> findPathToTerminus(int[] entry, double[][] rawDensities,
                                                   DensityProvider provider, RiverCellKey cellKey) {
        List<int[]> path = new ArrayList<>();
        boolean[][] visited = new boolean[SUBCELL_SIZE][SUBCELL_SIZE];
        int[] current = entry.clone();

        path.add(current.clone());
        visited[current[0]][current[1]] = true;

        int cellSize = RiverCellKey.getCellSize();
        double subcellStep = cellSize / (double) SUBCELL_SIZE;

        while (true) {
            int worldX = cellKey.worldX() + (int) (current[1] * subcellStep);
            int worldZ = cellKey.worldZ() + (int) (current[0] * subcellStep);

            if (provider.isOcean(worldX, worldZ) || provider.isLake(worldX, worldZ)) {
                return path;
            }

            List<int[]> unvisitedNeighbors = getUnvisitedNeighbors(current, visited);

            if (unvisitedNeighbors.isEmpty()) {
                return path;
            }

            List<int[]> lowerNeighbors = getLowerCostNeighbors(current, unvisitedNeighbors, rawDensities);

            if (!lowerNeighbors.isEmpty()) {
                current = findLowestCost(lowerNeighbors, rawDensities);
            } else {
                current = unvisitedNeighbors.get(0);
            }

            path.add(current.clone());
            visited[current[0]][current[1]] = true;

            if (path.size() > SUBCELL_SIZE * SUBCELL_SIZE) {
                return path;
            }
        }
    }

    private static double[][] applyExitWeighting(double[][] rawDensities, int[] exit) {
        return applyExitWeighting(rawDensities, exit, EXIT_WEIGHT_FACTOR);
    }

    private static double[][] applyExitWeighting(double[][] rawDensities, int[] exit, double weightFactor) {
        double[][] weighted = new double[SUBCELL_SIZE][SUBCELL_SIZE];

        for (int row = 0; row < SUBCELL_SIZE; row++) {
            for (int col = 0; col < SUBCELL_SIZE; col++) {
                int distance = Math.abs(row - exit[0]) + Math.abs(col - exit[1]);
                weighted[col][row] = rawDensities[col][row] + (distance * weightFactor);
            }
        }

        return weighted;
    }

    private static int[] getSubcellAtEdge(FlowDirection direction) {
        return switch (direction) {
            case NORTH -> new int[]{0, CENTER_SUBCELL};
            case SOUTH -> new int[]{SUBCELL_SIZE - 1, CENTER_SUBCELL};
            case EAST -> new int[]{CENTER_SUBCELL, SUBCELL_SIZE - 1};
            case WEST -> new int[]{CENTER_SUBCELL, 0};
            default -> new int[]{CENTER_SUBCELL, CENTER_SUBCELL};
        };
    }

    private static int[] getSourcePointNearEdge(FlowDirection direction) {
        return switch (direction) {
            case NORTH -> new int[]{1, CENTER_SUBCELL};
            case SOUTH -> new int[]{SUBCELL_SIZE - 2, CENTER_SUBCELL};
            case EAST -> new int[]{CENTER_SUBCELL, SUBCELL_SIZE - 2};
            case WEST -> new int[]{CENTER_SUBCELL, 1};
            default -> new int[]{CENTER_SUBCELL, CENTER_SUBCELL};
        };
    }

    private static boolean isAdjacent(int[] a, int[] b) {
        int rowDiff = Math.abs(a[0] - b[0]);
        int colDiff = Math.abs(a[1] - b[1]);
        return (rowDiff == 1 && colDiff == 0) || (rowDiff == 0 && colDiff == 1);
    }

    private static List<int[]> getUnvisitedNeighbors(int[] current, boolean[][] visited) {
        List<int[]> neighbors = new ArrayList<>();
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] dir : directions) {
            int newRow = current[0] + dir[0];
            int newCol = current[1] + dir[1];

            if (newRow >= 0 && newRow < SUBCELL_SIZE && newCol >= 0 && newCol < SUBCELL_SIZE) {
                if (!visited[newRow][newCol]) {
                    neighbors.add(new int[]{newRow, newCol});
                }
            }
        }

        return neighbors;
    }

    private static List<int[]> getLowerCostNeighbors(int[] current, List<int[]> neighbors, double[][] costs) {
        List<int[]> lower = new ArrayList<>();
        double currentCost = costs[current[1]][current[0]];

        for (int[] neighbor : neighbors) {
            if (costs[neighbor[1]][neighbor[0]] < currentCost) {
                lower.add(neighbor);
            }
        }

        return lower;
    }

    private static int[] findLowestCost(List<int[]> neighbors, double[][] costs) {
        int[] lowest = neighbors.get(0);
        double lowestCost = costs[lowest[1]][lowest[0]];

        for (int[] neighbor : neighbors) {
            double cost = costs[neighbor[1]][neighbor[0]];
            if (cost < lowestCost) {
                lowest = neighbor;
                lowestCost = cost;
            }
        }

        return lowest;
    }

    private static List<int[]> getCloserNeighbors(int[] current, List<int[]> neighbors, int[] exit) {
        List<int[]> closer = new ArrayList<>();
        int currentDistance = Math.abs(current[0] - exit[0]) + Math.abs(current[1] - exit[1]);

        for (int[] neighbor : neighbors) {
            int neighborDistance = Math.abs(neighbor[0] - exit[0]) + Math.abs(neighbor[1] - exit[1]);
            if (neighborDistance < currentDistance) {
                closer.add(neighbor);
            }
        }

        return closer;
    }

    private static int[] findWaterSubcell(RiverCellKey cellKey, DensityProvider provider) {
        int cellSize = RiverCellKey.getCellSize();
        double subcellStep = cellSize / (double) SUBCELL_SIZE;

        int totalRow = 0;
        int totalCol = 0;
        int waterCount = 0;

        for (int row = 0; row < SUBCELL_SIZE; row++) {
            for (int col = 0; col < SUBCELL_SIZE; col++) {
                int worldX = cellKey.worldX() + (int) (col * subcellStep);
                int worldZ = cellKey.worldZ() + (int) (row * subcellStep);

                if (provider.isOcean(worldX, worldZ) || provider.isLake(worldX, worldZ)) {
                    totalRow += row;
                    totalCol += col;
                    waterCount++;
                }
            }
        }

        if (waterCount > 0) {
            return new int[]{totalRow / waterCount, totalCol / waterCount};
        }

        return new int[]{CENTER_SUBCELL, CENTER_SUBCELL};
    }

    private static FlowDirection findTerminusDirection(RiverCell cell, RiverCellSavedData savedData) {
        FlowDirection[] cardinals = {FlowDirection.NORTH, FlowDirection.SOUTH, FlowDirection.EAST, FlowDirection.WEST};

        for (FlowDirection direction : cardinals) {
            RiverCellKey neighborKey = direction.neighbor(cell.getKey());
            RiverCell neighbor = savedData.getIfPresent(neighborKey);

            if (neighbor == null) {
                LOGGER.info("  findTerminusDirection: {} neighbor is null", direction);
                continue;
            }

            CellClassification neighborClass = neighbor.getClassification();
            LOGGER.info("  findTerminusDirection: {} neighbor classification={}", direction, neighborClass);

            if (neighborClass == CellClassification.OCEAN || neighborClass == CellClassification.LAKE) {
                return direction;
            }
        }

        for (FlowDirection direction : cardinals) {
            RiverCellKey neighborKey = direction.neighbor(cell.getKey());
            RiverCell neighbor = savedData.getIfPresent(neighborKey);

            if (neighbor == null) {
                continue;
            }

            CellClassification neighborClass = neighbor.getClassification();
            if (neighborClass == CellClassification.COASTAL || neighborClass == CellClassification.LAKESHORE) {
                LOGGER.info("  findTerminusDirection: falling back to shore neighbor {}", direction);
                return direction;
            }
        }

        return FlowDirection.NONE;
    }

    private static List<FlowDirection> sortByCardinalPriority(Set<FlowDirection> inputs) {
        List<FlowDirection> sorted = new ArrayList<>(inputs);
        Map<FlowDirection, Integer> priority = Map.of(
            FlowDirection.SOUTH, 0,
            FlowDirection.EAST, 1,
            FlowDirection.WEST, 2,
            FlowDirection.NORTH, 3
        );
        sorted.sort((a, b) -> {
            int pa = priority.getOrDefault(a, 99);
            int pb = priority.getOrDefault(b, 99);
            return Integer.compare(pa, pb);
        });
        return sorted;
    }

    private static List<int[]> findPathToTerminusOrExistingPath(int[] entry, double[][] costs,
                                                                   List<int[]> existingPath, Set<String> existingPathCells,
                                                                   DensityProvider provider, RiverCellKey cellKey) {
        List<int[]> path = new ArrayList<>();
        boolean[][] visited = new boolean[SUBCELL_SIZE][SUBCELL_SIZE];
        int[] current = entry.clone();

        path.add(current.clone());
        visited[current[0]][current[1]] = true;

        String startKey = current[0] + "," + current[1];
        if (existingPathCells.contains(startKey)) {
            return appendExistingPathFrom(path, current, existingPath);
        }

        int cellSize = RiverCellKey.getCellSize();
        double subcellStep = cellSize / (double) SUBCELL_SIZE;
        boolean checkForOcean = provider != null && cellKey != null;

        while (true) {
            if (checkForOcean) {
                int worldX = cellKey.worldX() + (int) (current[1] * subcellStep);
                int worldZ = cellKey.worldZ() + (int) (current[0] * subcellStep);

                if (provider.isOcean(worldX, worldZ) || provider.isLake(worldX, worldZ)) {
                    return path;
                }
            }

            List<int[]> unvisitedNeighbors = getUnvisitedNeighbors(current, visited);

            if (unvisitedNeighbors.isEmpty()) {
                return path;
            }

            current = findLowestCost(unvisitedNeighbors, costs);

            path.add(current.clone());
            visited[current[0]][current[1]] = true;

            String key = current[0] + "," + current[1];
            if (existingPathCells.contains(key)) {
                return appendExistingPathFrom(path, current, existingPath);
            }

            if (path.size() > SUBCELL_SIZE * SUBCELL_SIZE) {
                return path;
            }
        }
    }

    private static List<int[]> appendExistingPathFrom(List<int[]> path, int[] confluencePoint, List<int[]> existingPath) {
        int confluenceIndex = -1;
        for (int i = 0; i < existingPath.size(); i++) {
            int[] point = existingPath.get(i);
            if (point[0] == confluencePoint[0] && point[1] == confluencePoint[1]) {
                confluenceIndex = i;
                break;
            }
        }

        if (confluenceIndex < 0) {
            return path;
        }

        for (int i = confluenceIndex + 1; i < existingPath.size(); i++) {
            path.add(existingPath.get(i).clone());
        }

        return path;
    }
}
