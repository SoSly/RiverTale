package org.sosly.rivertale.worldgen.river;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.sosly.rivertale.worldgen.river.D8FlowCalculator.GRID_SIZE;

public class D8PathRefiner {

    private D8PathRefiner() {
    }

    public static D8FlowResult refine(
            RiverRegionKey cellKey,
            FlowDirection[][] flowDirection,
            RegionFeatureType featureType,
            FlowDirection[][][] neighborFlowDirections,
            RegionFeatureType[] neighborFeatureTypes,
            BiFunction<Integer, Integer, Double> densitySampler,
            BiFunction<Integer, Integer, Double> continentsSampler,
            BiFunction<Integer, Integer, Double> depthSampler,
            double oceanThreshold,
            double lakeThreshold) {

        int cellSize = RiverRegionKey.getRegionSize();
        int cellSpacing = cellSize / GRID_SIZE;
        int cellOriginX = cellKey.worldX();
        int cellOriginZ = cellKey.worldZ();

        EdgeCrossing[] crossings = new EdgeCrossing[4];
        double[] crossingStrengths = new double[4];

        boolean isShore = featureType == RegionFeatureType.SHORE;
        boolean isLand = featureType != RegionFeatureType.BODY && !isShore;

        if (featureType != RegionFeatureType.BODY) {
            computeEdgeCrossings(cellOriginX, cellOriginZ, cellSpacing, densitySampler,
                flowDirection, neighborFlowDirections, neighborFeatureTypes,
                crossings, crossingStrengths);
        }

        PathDirection primaryOutputDirection = isShore
            ? PathDirection.NONE
            : computePrimaryOutput(flowDirection, crossings);

        List<int[]> terminusList = new ArrayList<>();
        Map<PathDirection, List<int[]>> riverPaths = new HashMap<>();
        List<int[]> confluenceCells = new ArrayList<>();

        if (isLand && primaryOutputDirection != PathDirection.NONE) {
            tracePaths(flowDirection, crossings, primaryOutputDirection,
                riverPaths, confluenceCells);
        } else if (isLand && !hasAnyOutput(crossings)) {
            traceBasinPaths(crossings, riverPaths);
            collectPathEndpoints(riverPaths, terminusList);
        } else if (isShore) {
            traceShorePaths(flowDirection, crossings, cellOriginX, cellOriginZ, cellSpacing,
                continentsSampler, depthSampler, oceanThreshold, lakeThreshold,
                riverPaths, confluenceCells);
            collectPathEndpoints(riverPaths, terminusList);
        }

        int[][] terminusCells = terminusList.toArray(new int[0][]);

        if (isShore) {
            for (int i = 0; i < 4; i++) {
                if (crossings[i] != null && crossings[i].direction() == EdgeCrossing.Direction.OUT) {
                    crossings[i] = null;
                    crossingStrengths[i] = 0;
                }
            }
        }

        return new D8FlowResult(flowDirection, crossings,
            crossingStrengths, primaryOutputDirection, terminusCells,
            riverPaths, confluenceCells);
    }

    private static void computeEdgeCrossings(
            int cellOriginX, int cellOriginZ, int cellSpacing,
            BiFunction<Integer, Integer, Double> densitySampler,
            FlowDirection[][] flowDirection,
            FlowDirection[][][] neighborFlowDirections,
            RegionFeatureType[] neighborFeatureTypes,
            EdgeCrossing[] crossings, double[] crossingStrengths) {

        computeCrossing(cellOriginX, cellOriginZ, cellSpacing, densitySampler,
            flowDirection, neighborFlowDirections, neighborFeatureTypes,
            crossings, crossingStrengths, PathDirection.NORTH, -1);
        computeCrossing(cellOriginX, cellOriginZ, cellSpacing, densitySampler,
            flowDirection, neighborFlowDirections, neighborFeatureTypes,
            crossings, crossingStrengths, PathDirection.SOUTH, -1);
        computeCrossing(cellOriginX, cellOriginZ, cellSpacing, densitySampler,
            flowDirection, neighborFlowDirections, neighborFeatureTypes,
            crossings, crossingStrengths, PathDirection.EAST, -1);
        computeCrossing(cellOriginX, cellOriginZ, cellSpacing, densitySampler,
            flowDirection, neighborFlowDirections, neighborFeatureTypes,
            crossings, crossingStrengths, PathDirection.WEST, -1);
    }

    private static void computeCrossing(
            int cellOriginX, int cellOriginZ, int cellSpacing,
            BiFunction<Integer, Integer, Double> densitySampler,
            FlowDirection[][] flowDirection,
            FlowDirection[][][] neighborFlowDirections,
            RegionFeatureType[] neighborFeatureTypes,
            EdgeCrossing[] crossings, double[] crossingStrengths,
            PathDirection dir, int excludePos) {

        PathDirection oppositeDir = dir.opposite();
        RegionFeatureType neighborType = neighborFeatureTypes[dir.ordinal()];
        boolean neighborIsLand = neighborType != null
            && neighborType != RegionFeatureType.BODY
            && neighborType != RegionFeatureType.SHORE;
        boolean neighborCanProvideInput = neighborIsLand
            && neighborFlowDirections[dir.ordinal()] != null;

        int bestPos = -1;
        double lowestAvg = Double.MAX_VALUE;
        boolean bestIsOutput = false;

        for (int i = 1; i < GRID_SIZE - 1; i++) {
            if (i == excludePos) {
                continue;
            }

            FlowDirection ourD8 = D8FlowCalculator.getOurEdgeD8(flowDirection, dir, i);
            FlowDirection neighborD8 = FlowDirection.SINK;
            if (neighborFlowDirections[dir.ordinal()] != null) {
                neighborD8 = D8FlowCalculator.getNeighborEdgeD8(neighborFlowDirections[dir.ordinal()], dir, i);
            }

            boolean ourD8PointsToNeighbor = (ourD8 == FlowDirection.NORTH && dir == PathDirection.NORTH)
                || (ourD8 == FlowDirection.SOUTH && dir == PathDirection.SOUTH)
                || (ourD8 == FlowDirection.EAST && dir == PathDirection.EAST)
                || (ourD8 == FlowDirection.WEST && dir == PathDirection.WEST);
            boolean neighborD8PointsToUs = neighborCanProvideInput
                && ((neighborD8 == FlowDirection.NORTH && oppositeDir == PathDirection.NORTH)
                || (neighborD8 == FlowDirection.SOUTH && oppositeDir == PathDirection.SOUTH)
                || (neighborD8 == FlowDirection.EAST && oppositeDir == PathDirection.EAST)
                || (neighborD8 == FlowDirection.WEST && oppositeDir == PathDirection.WEST));

            if (!ourD8PointsToNeighbor && !neighborD8PointsToUs) {
                continue;
            }

            double[] densities = sampleEdgeDensities(cellOriginX, cellOriginZ, cellSpacing,
                densitySampler, dir, i);
            double avg = (densities[0] + densities[1]) / 2.0;

            if (avg < lowestAvg) {
                lowestAvg = avg;
                bestPos = i;

                if (ourD8PointsToNeighbor && !neighborD8PointsToUs) {
                    bestIsOutput = true;
                } else if (!ourD8PointsToNeighbor && neighborD8PointsToUs) {
                    bestIsOutput = false;
                } else {
                    bestIsOutput = densities[0] > densities[1];
                }
            }
        }

        if (bestPos < 0) {
            crossings[dir.ordinal()] = null;
            crossingStrengths[dir.ordinal()] = 0;
            return;
        }

        double[] densities = sampleEdgeDensities(cellOriginX, cellOriginZ, cellSpacing,
            densitySampler, dir, bestPos);
        double diff = Math.abs(densities[0] - densities[1]);

        int[] rowCol = posToRowCol(dir, bestPos);
        EdgeCrossing.Direction crossingDir = bestIsOutput
            ? EdgeCrossing.Direction.OUT
            : EdgeCrossing.Direction.IN;
        crossings[dir.ordinal()] = new EdgeCrossing(rowCol[0], rowCol[1], crossingDir);
        crossingStrengths[dir.ordinal()] = diff;
    }

    private static int[] posToRowCol(PathDirection dir, int pos) {
        return switch (dir) {
            case NORTH -> new int[]{0, pos};
            case SOUTH -> new int[]{GRID_SIZE - 1, pos};
            case EAST -> new int[]{pos, GRID_SIZE - 1};
            case WEST -> new int[]{pos, 0};
            default -> new int[]{-1, -1};
        };
    }

    private static double[] sampleEdgeDensities(
            int cellOriginX, int cellOriginZ, int cellSpacing,
            BiFunction<Integer, Integer, Double> densitySampler,
            PathDirection dir, int pos) {

        int ourX;
        int ourZ;
        int neighborX;
        int neighborZ;

        if (dir == PathDirection.NORTH) {
            ourX = cellOriginX + pos * cellSpacing + cellSpacing / 2;
            ourZ = cellOriginZ + cellSpacing / 2;
            neighborX = ourX;
            neighborZ = ourZ - cellSpacing;
        } else if (dir == PathDirection.SOUTH) {
            ourX = cellOriginX + pos * cellSpacing + cellSpacing / 2;
            ourZ = cellOriginZ + (GRID_SIZE - 1) * cellSpacing + cellSpacing / 2;
            neighborX = ourX;
            neighborZ = ourZ + cellSpacing;
        } else if (dir == PathDirection.EAST) {
            ourX = cellOriginX + (GRID_SIZE - 1) * cellSpacing + cellSpacing / 2;
            ourZ = cellOriginZ + pos * cellSpacing + cellSpacing / 2;
            neighborX = ourX + cellSpacing;
            neighborZ = ourZ;
        } else {
            ourX = cellOriginX + cellSpacing / 2;
            ourZ = cellOriginZ + pos * cellSpacing + cellSpacing / 2;
            neighborX = ourX - cellSpacing;
            neighborZ = ourZ;
        }

        return new double[] {
            densitySampler.apply(ourX, ourZ),
            densitySampler.apply(neighborX, neighborZ)
        };
    }

    private static PathDirection computePrimaryOutput(FlowDirection[][] flowDirection, EdgeCrossing[] crossings) {
        int sumX = 0;
        int sumZ = 0;

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                FlowDirection dir = flowDirection[row][col];
                switch (dir) {
                    case NORTH -> sumZ -= 1;
                    case SOUTH -> sumZ += 1;
                    case EAST -> sumX += 1;
                    case WEST -> sumX -= 1;
                    default -> { }
                }
            }
        }

        int[] dotProducts = new int[] {
            -sumZ,
            sumZ,
            sumX,
            -sumX
        };

        PathDirection bestDir = PathDirection.NONE;
        int bestDot = Integer.MIN_VALUE;

        for (int dir = 0; dir < 4; dir++) {
            EdgeCrossing crossing = crossings[dir];
            boolean isOutput = crossing != null && crossing.direction() == EdgeCrossing.Direction.OUT;
            if (isOutput && dotProducts[dir] > bestDot) {
                bestDot = dotProducts[dir];
                bestDir = PathDirection.values()[dir];
            }
        }

        return bestDir;
    }

    private static boolean hasAnyOutput(EdgeCrossing[] crossings) {
        for (EdgeCrossing crossing : crossings) {
            if (crossing != null && crossing.direction() == EdgeCrossing.Direction.OUT) {
                return true;
            }
        }
        return false;
    }

    private static void collectPathEndpoints(Map<PathDirection, List<int[]>> riverPaths, List<int[]> terminusList) {
        for (List<int[]> path : riverPaths.values()) {
            if (path.isEmpty()) {
                continue;
            }
            terminusList.add(path.get(path.size() - 1));
        }
    }

    private static int[] findThresholdTerminus(
            int cellOriginX, int cellOriginZ, int cellSpacing,
            BiFunction<Integer, Integer, Double> sampler, double threshold) {

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int worldX = cellOriginX + col * cellSpacing + cellSpacing / 2;
                int worldZ = cellOriginZ + row * cellSpacing + cellSpacing / 2;
                double value = sampler.apply(worldX, worldZ);
                if (value < threshold) {
                    return new int[]{row, col};
                }
            }
        }

        return new int[]{-1, -1};
    }

    public static void tracePaths(
            FlowDirection[][] flowDirection,
            EdgeCrossing[] crossings,
            PathDirection primaryOutputDirection,
            Map<PathDirection, List<int[]>> riverPaths,
            List<int[]> confluenceCells) {

        EdgeCrossing outputCrossing = crossings[primaryOutputDirection.ordinal()];
        if (outputCrossing == null) {
            return;
        }
        int[] outputCell = new int[]{outputCrossing.row(), outputCrossing.col()};

        List<int[]> inputCells = new ArrayList<>();
        List<PathDirection> inputDirections = new ArrayList<>();
        boolean[][] forbidden = new boolean[GRID_SIZE][GRID_SIZE];

        for (int dir = 0; dir < 4; dir++) {
            EdgeCrossing crossing = crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() == EdgeCrossing.Direction.IN) {
                inputCells.add(new int[]{crossing.row(), crossing.col()});
                inputDirections.add(PathDirection.values()[dir]);
            } else if (dir != primaryOutputDirection.ordinal()) {
                forbidden[crossing.row()][crossing.col()] = true;
            }
        }

        if (inputCells.isEmpty()) {
            return;
        }

        boolean[][] pathCovered = new boolean[GRID_SIZE][GRID_SIZE];

        for (int i = 0; i < inputCells.size(); i++) {
            int[] inputCell = inputCells.get(i);
            PathDirection inputDir = inputDirections.get(i);

            List<int[]> path = tracePathToOutput(
                flowDirection, inputCell, outputCell,
                pathCovered, forbidden, confluenceCells);

            for (int[] cell : path) {
                pathCovered[cell[0]][cell[1]] = true;
            }

            riverPaths.put(inputDir, path);
        }
    }

    private static void traceBasinPaths(
            EdgeCrossing[] crossings,
            Map<PathDirection, List<int[]>> riverPaths) {

        for (int dir = 0; dir < 4; dir++) {
            EdgeCrossing crossing = crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() != EdgeCrossing.Direction.IN) {
                continue;
            }

            List<int[]> path = List.of(new int[]{crossing.row(), crossing.col()});
            PathDirection pathDir = PathDirection.values()[dir];
            riverPaths.put(pathDir, path);
        }
    }

    private static void traceShorePaths(
            FlowDirection[][] flowDirection,
            EdgeCrossing[] crossings,
            int cellOriginX,
            int cellOriginZ,
            int cellSpacing,
            BiFunction<Integer, Integer, Double> continentsSampler,
            BiFunction<Integer, Integer, Double> depthSampler,
            double oceanThreshold,
            double lakeThreshold,
            Map<PathDirection, List<int[]>> riverPaths,
            List<int[]> confluenceCells) {

        List<int[]> oceanCells = new ArrayList<>();
        List<int[]> lakeCells = new ArrayList<>();
        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int worldX = cellOriginX + col * cellSpacing + cellSpacing / 2;
                int worldZ = cellOriginZ + row * cellSpacing + cellSpacing / 2;
                double continents = continentsSampler.apply(worldX, worldZ);
                if (continents < oceanThreshold) {
                    oceanCells.add(new int[]{row, col});
                } else {
                    double depth = depthSampler.apply(worldX, worldZ);
                    if (depth < lakeThreshold) {
                        lakeCells.add(new int[]{row, col});
                    }
                }
            }
        }

        List<int[]> targetCells = oceanCells.isEmpty() ? lakeCells : oceanCells;
        if (targetCells.isEmpty()) {
            return;
        }

        boolean[][] pathCovered = new boolean[GRID_SIZE][GRID_SIZE];
        boolean[][] forbidden = new boolean[GRID_SIZE][GRID_SIZE];

        for (int dir = 0; dir < 4; dir++) {
            EdgeCrossing crossing = crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() != EdgeCrossing.Direction.IN) {
                continue;
            }

            int[] inputCell = new int[]{crossing.row(), crossing.col()};
            PathDirection pathDir = PathDirection.values()[dir];

            int[] target = findNearestWaterCell(inputCell, targetCells);

            List<int[]> path = tracePathToWater(
                flowDirection, inputCell, target,
                pathCovered, forbidden, confluenceCells, targetCells);

            for (int[] cell : path) {
                pathCovered[cell[0]][cell[1]] = true;
            }

            riverPaths.put(pathDir, path);
        }
    }

    private static int[] findNearestWaterCell(int[] from, List<int[]> waterCells) {
        int[] nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (int[] water : waterCells) {
            int dist = distanceSquared(from[0], from[1], water[0], water[1]);
            if (dist < minDist) {
                minDist = dist;
                nearest = water;
            }
        }

        return nearest;
    }

    private static boolean isWater(int worldX, int worldZ,
            BiFunction<Integer, Integer, Double> continentsSampler,
            BiFunction<Integer, Integer, Double> depthSampler,
            double oceanThreshold, double lakeThreshold) {

        double continents = continentsSampler.apply(worldX, worldZ);
        if (continents < oceanThreshold) {
            return true;
        }
        double depth = depthSampler.apply(worldX, worldZ);
        return depth < lakeThreshold;
    }

    private static List<int[]> tracePathToWater(
            FlowDirection[][] flowDirection,
            int[] start,
            int[] target,
            boolean[][] pathCovered,
            boolean[][] forbidden,
            List<int[]> confluenceCells,
            List<int[]> waterCells) {

        List<int[]> path = new ArrayList<>();
        boolean[][] visited = new boolean[GRID_SIZE][GRID_SIZE];
        boolean confluenceMarked = false;

        int row = start[0];
        int col = start[1];

        while (true) {
            path.add(new int[]{row, col});
            visited[row][col] = true;

            if (pathCovered[row][col] && !confluenceMarked) {
                confluenceCells.add(new int[]{row, col});
                confluenceMarked = true;
            }

            if (isInList(row, col, waterCells)) {
                break;
            }

            FlowDirection d8Dir = flowDirection[row][col];
            int[] d8Next = getNeighborInDirection(row, col, d8Dir);
            int currentDist = distanceSquared(row, col, target[0], target[1]);

            if (d8Dir != FlowDirection.SINK && isValidMove(d8Next, visited, forbidden)) {
                int d8Dist = distanceSquared(d8Next[0], d8Next[1], target[0], target[1]);
                if (d8Dist < currentDist) {
                    row = d8Next[0];
                    col = d8Next[1];
                    continue;
                }
            }

            int[] closest = findClosestNeighbor(row, col, target, flowDirection, visited, forbidden);
            if (closest == null) {
                break;
            }
            row = closest[0];
            col = closest[1];
        }

        return path;
    }

    private static boolean isInList(int row, int col, List<int[]> cells) {
        for (int[] cell : cells) {
            if (cell[0] == row && cell[1] == col) {
                return true;
            }
        }
        return false;
    }

    private static List<int[]> tracePathToOutput(
            FlowDirection[][] flowDirection,
            int[] start,
            int[] target,
            boolean[][] pathCovered,
            boolean[][] forbidden,
            List<int[]> confluenceCells) {

        List<int[]> path = new ArrayList<>();
        boolean[][] visited = new boolean[GRID_SIZE][GRID_SIZE];
        boolean confluenceMarked = false;

        int row = start[0];
        int col = start[1];

        while (true) {
            path.add(new int[]{row, col});
            visited[row][col] = true;

            if (pathCovered[row][col] && !confluenceMarked) {
                confluenceCells.add(new int[]{row, col});
                confluenceMarked = true;
            }

            if (row == target[0] && col == target[1]) {
                break;
            }

            FlowDirection d8Dir = flowDirection[row][col];
            int[] d8Next = getNeighborInDirection(row, col, d8Dir);
            int currentDist = distanceSquared(row, col, target[0], target[1]);

            if (d8Dir != FlowDirection.SINK && isValidMove(d8Next, visited, forbidden)) {
                int d8Dist = distanceSquared(d8Next[0], d8Next[1], target[0], target[1]);
                if (d8Dist < currentDist) {
                    row = d8Next[0];
                    col = d8Next[1];
                    continue;
                }
            }

            int[] closest = findClosestNeighbor(row, col, target, flowDirection, visited, forbidden);
            if (closest == null) {
                break;
            }
            row = closest[0];
            col = closest[1];
        }

        return path;
    }

    private static int[] getNeighborInDirection(int row, int col, FlowDirection dir) {
        return switch (dir) {
            case NORTH -> new int[]{row - 1, col};
            case SOUTH -> new int[]{row + 1, col};
            case EAST -> new int[]{row, col + 1};
            case WEST -> new int[]{row, col - 1};
            case NORTHEAST -> new int[]{row - 1, col + 1};
            case NORTHWEST -> new int[]{row - 1, col - 1};
            case SOUTHEAST -> new int[]{row + 1, col + 1};
            case SOUTHWEST -> new int[]{row + 1, col - 1};
            default -> new int[]{-1, -1};
        };
    }

    private static int[] findClosestNeighbor(
            int row, int col, int[] target,
            FlowDirection[][] flowDirection,
            boolean[][] visited, boolean[][] forbidden) {

        int[][] neighbors = {
            {row - 1, col},
            {row + 1, col},
            {row, col + 1},
            {row, col - 1}
        };

        int currentDist = distanceSquared(row, col, target[0], target[1]);
        FlowDirection currentD8 = flowDirection[row][col];

        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;

        for (int[] neighbor : neighbors) {
            if (!isValidMove(neighbor, visited, forbidden)) {
                continue;
            }

            int dist = distanceSquared(neighbor[0], neighbor[1], target[0], target[1]);
            if (dist >= currentDist) {
                continue;
            }

            int moveRow = neighbor[0] - row;
            int moveCol = neighbor[1] - col;
            int score = computeMoveAlignment(moveRow, moveCol, currentD8);

            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                bestScore = score;
                bestDist = dist;
                best = neighbor;
            }
        }

        return best;
    }

    private static int computeMoveAlignment(int moveRow, int moveCol, FlowDirection d8Dir) {
        int d8Row = 0;
        int d8Col = 0;
        switch (d8Dir) {
            case NORTH -> d8Row = -1;
            case SOUTH -> d8Row = 1;
            case EAST -> d8Col = 1;
            case WEST -> d8Col = -1;
            case NORTHEAST -> { d8Row = -1; d8Col = 1; }
            case NORTHWEST -> { d8Row = -1; d8Col = -1; }
            case SOUTHEAST -> { d8Row = 1; d8Col = 1; }
            case SOUTHWEST -> { d8Row = 1; d8Col = -1; }
            default -> { }
        }
        return moveRow * d8Row + moveCol * d8Col;
    }

    private static boolean isValidMove(int[] pos, boolean[][] visited, boolean[][] forbidden) {
        int row = pos[0];
        int col = pos[1];

        if (row < 0 || row >= GRID_SIZE || col < 0 || col >= GRID_SIZE) {
            return false;
        }
        if (visited[row][col]) {
            return false;
        }
        if (forbidden[row][col]) {
            return false;
        }
        return true;
    }

    private static int distanceSquared(int r1, int c1, int r2, int c2) {
        int dr = r1 - r2;
        int dc = c1 - c2;
        return dr * dr + dc * dc;
    }
}
