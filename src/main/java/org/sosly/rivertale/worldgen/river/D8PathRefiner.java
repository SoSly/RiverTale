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
            RegionClassification classification,
            FlowDirection[][][] neighborFlowDirections,
            RegionClassification[] neighborClassifications,
            BiFunction<Integer, Integer, Double> densitySampler,
            BiFunction<Integer, Integer, Double> continentsSampler,
            BiFunction<Integer, Integer, Double> depthSampler,
            double oceanThreshold,
            double lakeThreshold) {

        int cellSize = RiverRegionKey.getRegionSize();
        int subcellSpacing = cellSize / GRID_SIZE;
        int cellOriginX = cellKey.worldX();
        int cellOriginZ = cellKey.worldZ();

        EdgeCrossing[] crossings = new EdgeCrossing[4];
        double[] crossingStrengths = new double[4];

        boolean isTerminus = classification == RegionClassification.COASTAL
            || classification == RegionClassification.LAKESHORE;

        if (!isTerminus) {
            computeEdgeCrossings(cellOriginX, cellOriginZ, subcellSpacing, densitySampler,
                flowDirection, neighborFlowDirections, neighborClassifications,
                crossings, crossingStrengths);
        }

        PathDirection primaryOutputDirection = computePrimaryOutput(flowDirection, crossings);

        boolean isBasin = !hasAnyOutput(crossings) && !isTerminus;

        List<int[]> terminusList = new ArrayList<>();

        if (classification == RegionClassification.COASTAL) {
            int[] terminus = findThresholdTerminus(cellOriginX, cellOriginZ, subcellSpacing,
                continentsSampler, oceanThreshold);
            if (terminus[0] >= 0) {
                terminusList.add(terminus);
            }
        } else if (classification == RegionClassification.LAKESHORE) {
            int[] terminus = findThresholdTerminus(cellOriginX, cellOriginZ, subcellSpacing,
                depthSampler, lakeThreshold);
            if (terminus[0] >= 0) {
                terminusList.add(terminus);
            }
        } else if (isBasin) {
            collectBasinTerminuses(crossings, terminusList);
        }

        int[][] terminusSubcells = terminusList.toArray(new int[0][]);

        Map<PathDirection, List<int[]>> riverPaths = new HashMap<>();
        List<int[]> confluenceSubcells = new ArrayList<>();

        if (classification == RegionClassification.LAND && primaryOutputDirection != PathDirection.NONE) {
            tracePaths(flowDirection, crossings, primaryOutputDirection,
                riverPaths, confluenceSubcells);
        }

        return new D8FlowResult(flowDirection, crossings,
            crossingStrengths, primaryOutputDirection, isBasin, terminusSubcells,
            riverPaths, confluenceSubcells);
    }

    private static void computeEdgeCrossings(
            int cellOriginX, int cellOriginZ, int subcellSpacing,
            BiFunction<Integer, Integer, Double> densitySampler,
            FlowDirection[][] flowDirection,
            FlowDirection[][][] neighborFlowDirections,
            RegionClassification[] neighborClassifications,
            EdgeCrossing[] crossings, double[] crossingStrengths) {

        computeCrossing(cellOriginX, cellOriginZ, subcellSpacing, densitySampler,
            flowDirection, neighborFlowDirections, neighborClassifications,
            crossings, crossingStrengths, PathDirection.NORTH, -1);
        computeCrossing(cellOriginX, cellOriginZ, subcellSpacing, densitySampler,
            flowDirection, neighborFlowDirections, neighborClassifications,
            crossings, crossingStrengths, PathDirection.SOUTH, -1);
        computeCrossing(cellOriginX, cellOriginZ, subcellSpacing, densitySampler,
            flowDirection, neighborFlowDirections, neighborClassifications,
            crossings, crossingStrengths, PathDirection.EAST, -1);
        computeCrossing(cellOriginX, cellOriginZ, subcellSpacing, densitySampler,
            flowDirection, neighborFlowDirections, neighborClassifications,
            crossings, crossingStrengths, PathDirection.WEST, -1);
    }

    private static void computeCrossing(
            int cellOriginX, int cellOriginZ, int subcellSpacing,
            BiFunction<Integer, Integer, Double> densitySampler,
            FlowDirection[][] flowDirection,
            FlowDirection[][][] neighborFlowDirections,
            RegionClassification[] neighborClassifications,
            EdgeCrossing[] crossings, double[] crossingStrengths,
            PathDirection dir, int excludePos) {

        PathDirection oppositeDir = dir.opposite();
        boolean neighborCanProvideInput = neighborClassifications[dir.ordinal()] == RegionClassification.LAND
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

            double[] densities = sampleEdgeDensities(cellOriginX, cellOriginZ, subcellSpacing,
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

        double[] densities = sampleEdgeDensities(cellOriginX, cellOriginZ, subcellSpacing,
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
            int cellOriginX, int cellOriginZ, int subcellSpacing,
            BiFunction<Integer, Integer, Double> densitySampler,
            PathDirection dir, int pos) {

        int ourX;
        int ourZ;
        int neighborX;
        int neighborZ;

        if (dir == PathDirection.NORTH) {
            ourX = cellOriginX + pos * subcellSpacing + subcellSpacing / 2;
            ourZ = cellOriginZ + subcellSpacing / 2;
            neighborX = ourX;
            neighborZ = ourZ - subcellSpacing;
        } else if (dir == PathDirection.SOUTH) {
            ourX = cellOriginX + pos * subcellSpacing + subcellSpacing / 2;
            ourZ = cellOriginZ + (GRID_SIZE - 1) * subcellSpacing + subcellSpacing / 2;
            neighborX = ourX;
            neighborZ = ourZ + subcellSpacing;
        } else if (dir == PathDirection.EAST) {
            ourX = cellOriginX + (GRID_SIZE - 1) * subcellSpacing + subcellSpacing / 2;
            ourZ = cellOriginZ + pos * subcellSpacing + subcellSpacing / 2;
            neighborX = ourX + subcellSpacing;
            neighborZ = ourZ;
        } else {
            ourX = cellOriginX + subcellSpacing / 2;
            ourZ = cellOriginZ + pos * subcellSpacing + subcellSpacing / 2;
            neighborX = ourX - subcellSpacing;
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

    private static void collectBasinTerminuses(EdgeCrossing[] crossings, List<int[]> terminusList) {
        for (int dir = 0; dir < 4; dir++) {
            EdgeCrossing crossing = crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() == EdgeCrossing.Direction.OUT) {
                continue;
            }
            terminusList.add(new int[]{crossing.row(), crossing.col()});
        }
    }

    private static int[] findThresholdTerminus(
            int cellOriginX, int cellOriginZ, int subcellSpacing,
            BiFunction<Integer, Integer, Double> sampler, double threshold) {

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int worldX = cellOriginX + col * subcellSpacing + subcellSpacing / 2;
                int worldZ = cellOriginZ + row * subcellSpacing + subcellSpacing / 2;
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
            List<int[]> confluenceSubcells) {

        EdgeCrossing outputCrossing = crossings[primaryOutputDirection.ordinal()];
        if (outputCrossing == null) {
            return;
        }
        int[] outputSubcell = new int[]{outputCrossing.row(), outputCrossing.col()};

        List<int[]> inputSubcells = new ArrayList<>();
        List<PathDirection> inputDirections = new ArrayList<>();
        boolean[][] forbidden = new boolean[GRID_SIZE][GRID_SIZE];

        for (int dir = 0; dir < 4; dir++) {
            EdgeCrossing crossing = crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() == EdgeCrossing.Direction.IN) {
                inputSubcells.add(new int[]{crossing.row(), crossing.col()});
                inputDirections.add(PathDirection.values()[dir]);
            } else if (dir != primaryOutputDirection.ordinal()) {
                forbidden[crossing.row()][crossing.col()] = true;
            }
        }

        if (inputSubcells.isEmpty()) {
            return;
        }

        boolean[][] pathCovered = new boolean[GRID_SIZE][GRID_SIZE];

        for (int i = 0; i < inputSubcells.size(); i++) {
            int[] inputSubcell = inputSubcells.get(i);
            PathDirection inputDir = inputDirections.get(i);

            List<int[]> path = tracePathToOutput(
                flowDirection, inputSubcell, outputSubcell,
                pathCovered, forbidden, confluenceSubcells);

            for (int[] subcell : path) {
                pathCovered[subcell[0]][subcell[1]] = true;
            }

            riverPaths.put(inputDir, path);
        }
    }

    private static List<int[]> tracePathToOutput(
            FlowDirection[][] flowDirection,
            int[] start,
            int[] target,
            boolean[][] pathCovered,
            boolean[][] forbidden,
            List<int[]> confluenceSubcells) {

        List<int[]> path = new ArrayList<>();
        boolean[][] visited = new boolean[GRID_SIZE][GRID_SIZE];
        boolean confluenceMarked = false;

        int row = start[0];
        int col = start[1];

        while (true) {
            path.add(new int[]{row, col});
            visited[row][col] = true;

            if (pathCovered[row][col] && !confluenceMarked) {
                confluenceSubcells.add(new int[]{row, col});
                confluenceMarked = true;
            }

            if (row == target[0] && col == target[1]) {
                break;
            }

            FlowDirection d8Dir = flowDirection[row][col];
            int[] d8Next = getNeighborInDirection(row, col, d8Dir);
            int currentDist = manhattanDistance(row, col, target[0], target[1]);

            if (d8Dir != FlowDirection.SINK && isValidMove(d8Next, visited, forbidden)) {
                int d8Dist = manhattanDistance(d8Next[0], d8Next[1], target[0], target[1]);
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

        int currentDist = manhattanDistance(row, col, target[0], target[1]);

        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;

        for (int[] neighbor : neighbors) {
            if (!isValidMove(neighbor, visited, forbidden)) {
                continue;
            }

            int dist = manhattanDistance(neighbor[0], neighbor[1], target[0], target[1]);
            if (dist >= currentDist) {
                continue;
            }

            FlowDirection d8Dir = flowDirection[neighbor[0]][neighbor[1]];
            int score = computeD8Alignment(neighbor, target, d8Dir);

            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                bestScore = score;
                bestDist = dist;
                best = neighbor;
            }
        }

        return best;
    }

    private static int computeD8Alignment(int[] from, int[] target, FlowDirection d8Dir) {
        int targetDRow = Integer.signum(target[0] - from[0]);
        int targetDCol = Integer.signum(target[1] - from[1]);

        int d8DRow = 0;
        int d8DCol = 0;
        switch (d8Dir) {
            case NORTH -> d8DRow = -1;
            case SOUTH -> d8DRow = 1;
            case EAST -> d8DCol = 1;
            case WEST -> d8DCol = -1;
            case NORTHEAST -> {
                d8DRow = -1;
                d8DCol = 1;
            }
            case NORTHWEST -> {
                d8DRow = -1;
                d8DCol = -1;
            }
            case SOUTHEAST -> {
                d8DRow = 1;
                d8DCol = 1;
            }
            case SOUTHWEST -> {
                d8DRow = 1;
                d8DCol = -1;
            }
            default -> { }
        }

        return targetDRow * d8DRow + targetDCol * d8DCol;
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

    private static int manhattanDistance(int r1, int c1, int r2, int c2) {
        return Math.abs(r1 - r2) + Math.abs(c1 - c2);
    }
}
