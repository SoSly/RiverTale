package org.sosly.rivertale.path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;

public class Refiner {

    private Refiner() {
    }

    public static Flow refine(
            RegionPos regionPos,
            Direction[][] flowDirection,
            RegionType featureRegionType,
            Direction[][][] neighborFlowDirections,
            RegionType[] neighborFeatureRegionTypes,
            BiFunction<Integer, Integer, Double> densitySampler,
            BiFunction<Integer, Integer, Double> continentsSampler,
            BiFunction<Integer, Integer, Double> depthSampler,
            double oceanThreshold,
            double lakeThreshold) {

        int cellSize = CellPos.getCellSize();
        int regionOriginX = regionPos.worldX();
        int regionOriginZ = regionPos.worldZ();

        Crossing[] crossings = new Crossing[4];
        double[] crossingStrengths = new double[4];

        boolean isShore = featureRegionType == RegionType.SHORE;
        boolean isLand = featureRegionType != RegionType.BODY && !isShore;

        if (featureRegionType != RegionType.BODY) {
            computeEdgeCrossings(regionOriginX, regionOriginZ, cellSize, densitySampler,
                flowDirection, neighborFlowDirections, neighborFeatureRegionTypes,
                crossings, crossingStrengths);
        }

        Direction primaryOutputDirection = isShore
            ? Direction.NONE
            : computePrimaryOutput(flowDirection, crossings);

        List<CellPos> terminusList = new ArrayList<>();
        Map<Direction, List<CellPos>> riverPaths = new HashMap<>();
        List<CellPos> confluenceCells = new ArrayList<>();

        if (isLand && primaryOutputDirection != Direction.NONE) {
            tracePaths(regionPos, flowDirection, crossings, primaryOutputDirection,
                riverPaths, confluenceCells);
        } else if (isLand && !hasAnyOutput(crossings)) {
            traceBasinPaths(regionPos, crossings, riverPaths);
            collectPathEndpoints(riverPaths, terminusList);
        } else if (isShore) {
            traceShorePaths(regionPos, flowDirection, crossings, regionOriginX, regionOriginZ, cellSize,
                continentsSampler, depthSampler, oceanThreshold, lakeThreshold,
                riverPaths, confluenceCells);
            collectPathEndpoints(riverPaths, terminusList);
        }

        if (isShore) {
            for (int i = 0; i < 4; i++) {
                if (crossings[i] != null && crossings[i].direction() == Crossing.Direction.OUT) {
                    crossings[i] = null;
                    crossingStrengths[i] = 0;
                }
            }
        }

        return new Flow(flowDirection, crossings,
            crossingStrengths, primaryOutputDirection, terminusList,
            riverPaths, confluenceCells);
    }

    private static void computeEdgeCrossings(
            int regionOriginX, int regionOriginZ, int cellSize,
            BiFunction<Integer, Integer, Double> densitySampler,
            Direction[][] flowDirection,
            Direction[][][] neighborFlowDirections,
            RegionType[] neighborFeatureRegionTypes,
            Crossing[] crossings, double[] crossingStrengths) {

        computeCrossing(regionOriginX, regionOriginZ, cellSize, densitySampler,
            flowDirection, neighborFlowDirections, neighborFeatureRegionTypes,
            crossings, crossingStrengths, Direction.NORTH, -1);
        computeCrossing(regionOriginX, regionOriginZ, cellSize, densitySampler,
            flowDirection, neighborFlowDirections, neighborFeatureRegionTypes,
            crossings, crossingStrengths, Direction.SOUTH, -1);
        computeCrossing(regionOriginX, regionOriginZ, cellSize, densitySampler,
            flowDirection, neighborFlowDirections, neighborFeatureRegionTypes,
            crossings, crossingStrengths, Direction.EAST, -1);
        computeCrossing(regionOriginX, regionOriginZ, cellSize, densitySampler,
            flowDirection, neighborFlowDirections, neighborFeatureRegionTypes,
            crossings, crossingStrengths, Direction.WEST, -1);
    }

    private static void computeCrossing(
            int regionOriginX, int regionOriginZ, int cellSize,
            BiFunction<Integer, Integer, Double> densitySampler,
            Direction[][] flowDirection,
            Direction[][][] neighborFlowDirections,
            RegionType[] neighborFeatureRegionTypes,
            Crossing[] crossings, double[] crossingStrengths,
            Direction dir, int excludePos) {

        Direction oppositeDir = dir.opposite();
        RegionType neighborRegionType = neighborFeatureRegionTypes[dir.ordinal()];
        boolean neighborIsLand = neighborRegionType != null
            && neighborRegionType != RegionType.BODY
            && neighborRegionType != RegionType.SHORE;
        boolean neighborCanProvideInput = neighborIsLand
            && neighborFlowDirections[dir.ordinal()] != null;

        int bestPos = -1;
        double lowestAvg = Double.MAX_VALUE;
        boolean bestIsOutput = false;

        for (int i = 1; i < RiverConfig.CELLS_PER_REGION.get() - 1; i++) {
            if (i == excludePos) {
                continue;
            }

            Direction ourD8 = FlowCalculator.getOuterEdge(flowDirection, dir, i);
            Direction neighborD8 = Direction.NONE;
            if (neighborFlowDirections[dir.ordinal()] != null) {
                neighborD8 = FlowCalculator.getNeighborEdge(neighborFlowDirections[dir.ordinal()], dir, i);
            }

            boolean ourD8PointsToNeighbor = (ourD8 == Direction.NORTH && dir == Direction.NORTH)
                || (ourD8 == Direction.SOUTH && dir == Direction.SOUTH)
                || (ourD8 == Direction.EAST && dir == Direction.EAST)
                || (ourD8 == Direction.WEST && dir == Direction.WEST);
            boolean neighborD8PointsToUs = neighborCanProvideInput
                && ((neighborD8 == Direction.NORTH && oppositeDir == Direction.NORTH)
                || (neighborD8 == Direction.SOUTH && oppositeDir == Direction.SOUTH)
                || (neighborD8 == Direction.EAST && oppositeDir == Direction.EAST)
                || (neighborD8 == Direction.WEST && oppositeDir == Direction.WEST));

            if (!ourD8PointsToNeighbor && !neighborD8PointsToUs) {
                continue;
            }

            double[] densities = sampleEdgeDensities(regionOriginX, regionOriginZ, cellSize,
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

        double[] densities = sampleEdgeDensities(regionOriginX, regionOriginZ, cellSize,
            densitySampler, dir, bestPos);
        double diff = Math.abs(densities[0] - densities[1]);

        int edgeIndex = RiverConfig.CELLS_PER_REGION.get() - 1;
        int row = switch (dir) {
            case NORTH -> 0;
            case SOUTH -> edgeIndex;
            case EAST, WEST -> bestPos;
            default -> -1;
        };
        int col = switch (dir) {
            case NORTH, SOUTH -> bestPos;
            case EAST -> edgeIndex;
            case WEST -> 0;
            default -> -1;
        };

        Crossing.Direction crossingDir = bestIsOutput
            ? Crossing.Direction.OUT
            : Crossing.Direction.IN;
        crossings[dir.ordinal()] = new Crossing(row, col, crossingDir);
        crossingStrengths[dir.ordinal()] = diff;
    }

    private static double[] sampleEdgeDensities(
            int regionOriginX, int regionOriginZ, int cellSize,
            BiFunction<Integer, Integer, Double> densitySampler,
            Direction dir, int pos) {

        int ourX;
        int ourZ;
        int neighborX;
        int neighborZ;

        if (dir == Direction.NORTH) {
            ourX = regionOriginX + pos * cellSize + cellSize / 2;
            ourZ = regionOriginZ + cellSize / 2;
            neighborX = ourX;
            neighborZ = ourZ - cellSize;
        } else if (dir == Direction.SOUTH) {
            ourX = regionOriginX + pos * cellSize + cellSize / 2;
            ourZ = regionOriginZ + (RiverConfig.CELLS_PER_REGION.get() - 1) * cellSize + cellSize / 2;
            neighborX = ourX;
            neighborZ = ourZ + cellSize;
        } else if (dir == Direction.EAST) {
            ourX = regionOriginX + (RiverConfig.CELLS_PER_REGION.get() - 1) * cellSize + cellSize / 2;
            ourZ = regionOriginZ + pos * cellSize + cellSize / 2;
            neighborX = ourX + cellSize;
            neighborZ = ourZ;
        } else {
            ourX = regionOriginX + cellSize / 2;
            ourZ = regionOriginZ + pos * cellSize + cellSize / 2;
            neighborX = ourX - cellSize;
            neighborZ = ourZ;
        }

        return new double[] {
            densitySampler.apply(ourX, ourZ),
            densitySampler.apply(neighborX, neighborZ)
        };
    }

    private static Direction computePrimaryOutput(Direction[][] flowDirection, Crossing[] crossings) {
        int sumX = 0;
        int sumZ = 0;

        for (int row = 0; row < RiverConfig.CELLS_PER_REGION.get(); row++) {
            for (int col = 0; col < RiverConfig.CELLS_PER_REGION.get(); col++) {
                Direction dir = flowDirection[row][col];
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

        Direction bestDir = Direction.NONE;
        int bestDot = Integer.MIN_VALUE;

        for (int dir = 0; dir < 4; dir++) {
            Crossing crossing = crossings[dir];
            boolean isOutput = crossing != null && crossing.direction() == Crossing.Direction.OUT;
            if (isOutput && dotProducts[dir] > bestDot) {
                bestDot = dotProducts[dir];
                bestDir = Direction.values()[dir];
            }
        }

        return bestDir;
    }

    private static boolean hasAnyOutput(Crossing[] crossings) {
        for (Crossing crossing : crossings) {
            if (crossing != null && crossing.direction() == Crossing.Direction.OUT) {
                return true;
            }
        }
        return false;
    }

    private static void collectPathEndpoints(Map<Direction, List<CellPos>> riverPaths, List<CellPos> terminusList) {
        for (List<CellPos> path : riverPaths.values()) {
            if (path.isEmpty()) {
                continue;
            }
            terminusList.add(path.get(path.size() - 1));
        }
    }

    public static void tracePaths(
            RegionPos regionPos,
            Direction[][] flowDirection,
            Crossing[] crossings,
            Direction primaryOutputDirection,
            Map<Direction, List<CellPos>> riverPaths,
            List<CellPos> confluenceCells) {

        Crossing outputCrossing = crossings[primaryOutputDirection.ordinal()];
        if (outputCrossing == null) {
            return;
        }
        CellPos outputCell = CellPos.fromLocal(regionPos, outputCrossing.row(), outputCrossing.col());

        List<CellPos> inputCells = new ArrayList<>();
        List<Direction> inputDirections = new ArrayList<>();
        int cellsPerRegion = RiverConfig.CELLS_PER_REGION.get();
        boolean[][] forbidden = new boolean[cellsPerRegion][cellsPerRegion];

        for (int dir = 0; dir < 4; dir++) {
            Crossing crossing = crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() == Crossing.Direction.IN) {
                inputCells.add(CellPos.fromLocal(regionPos, crossing.row(), crossing.col()));
                inputDirections.add(Direction.values()[dir]);
            } else if (dir != primaryOutputDirection.ordinal()) {
                forbidden[crossing.row()][crossing.col()] = true;
            }
        }

        if (inputCells.isEmpty()) {
            return;
        }

        boolean[][] pathCovered = new boolean[cellsPerRegion][cellsPerRegion];

        for (int i = 0; i < inputCells.size(); i++) {
            CellPos inputCell = inputCells.get(i);
            Direction inputDir = inputDirections.get(i);

            List<CellPos> path = tracePathToOutput(
                regionPos, flowDirection, inputCell, outputCell,
                pathCovered, forbidden, confluenceCells);

            for (CellPos cell : path) {
                pathCovered[cell.row()][cell.col()] = true;
            }

            riverPaths.put(inputDir, path);
        }
    }

    private static void traceBasinPaths(
            RegionPos regionPos,
            Crossing[] crossings,
            Map<Direction, List<CellPos>> riverPaths) {

        for (int dir = 0; dir < 4; dir++) {
            Crossing crossing = crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() != Crossing.Direction.IN) {
                continue;
            }

            CellPos cell = CellPos.fromLocal(regionPos, crossing.row(), crossing.col());
            List<CellPos> path = List.of(cell);
            Direction pathDir = Direction.values()[dir];
            riverPaths.put(pathDir, path);
        }
    }

    private static void traceShorePaths(
            RegionPos regionPos,
            Direction[][] flowDirection,
            Crossing[] crossings,
            int regionOriginX,
            int regionOriginZ,
            int cellSize,
            BiFunction<Integer, Integer, Double> continentsSampler,
            BiFunction<Integer, Integer, Double> depthSampler,
            double oceanThreshold,
            double lakeThreshold,
            Map<Direction, List<CellPos>> riverPaths,
            List<CellPos> confluenceCells) {

        int cellsPerRegion = RiverConfig.CELLS_PER_REGION.get();
        List<CellPos> oceanCells = new ArrayList<>();
        List<CellPos> lakeCells = new ArrayList<>();
        for (int row = 0; row < cellsPerRegion; row++) {
            for (int col = 0; col < cellsPerRegion; col++) {
                int worldX = regionOriginX + col * cellSize + cellSize / 2;
                int worldZ = regionOriginZ + row * cellSize + cellSize / 2;
                double continents = continentsSampler.apply(worldX, worldZ);
                if (continents < oceanThreshold) {
                    oceanCells.add(CellPos.fromLocal(regionPos, row, col));
                } else {
                    double depth = depthSampler.apply(worldX, worldZ);
                    if (depth < lakeThreshold) {
                        lakeCells.add(CellPos.fromLocal(regionPos, row, col));
                    }
                }
            }
        }

        List<CellPos> targetCells = oceanCells.isEmpty() ? lakeCells : oceanCells;
        if (targetCells.isEmpty()) {
            return;
        }

        boolean[][] pathCovered = new boolean[cellsPerRegion][cellsPerRegion];
        boolean[][] forbidden = new boolean[cellsPerRegion][cellsPerRegion];

        for (int dir = 0; dir < 4; dir++) {
            Crossing crossing = crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (crossing.direction() != Crossing.Direction.IN) {
                continue;
            }

            CellPos inputCell = CellPos.fromLocal(regionPos, crossing.row(), crossing.col());
            Direction pathDir = Direction.values()[dir];

            List<CellPos> path = tracePathToWater(
                regionPos, flowDirection, inputCell,
                pathCovered, forbidden, confluenceCells, targetCells);

            for (CellPos cell : path) {
                pathCovered[cell.row()][cell.col()] = true;
            }

            riverPaths.put(pathDir, path);
        }
    }

    private static CellPos findNearestWaterCell(CellPos from, List<CellPos> waterCells) {
        CellPos nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (CellPos water : waterCells) {
            int dist = distanceSquared(from.row(), from.col(), water.row(), water.col());
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

    private static List<CellPos> tracePathToWater(
            RegionPos regionPos,
            Direction[][] flowDirection,
            CellPos start,
            boolean[][] pathCovered,
            boolean[][] forbidden,
            List<CellPos> confluenceCells,
            List<CellPos> waterCells) {

        List<CellPos> path = new ArrayList<>();
        int cellsPerRegion = RiverConfig.CELLS_PER_REGION.get();
        boolean[][] visited = new boolean[cellsPerRegion][cellsPerRegion];
        boolean confluenceMarked = false;

        CellPos current = start;

        while (true) {
            int row = current.row();
            int col = current.col();

            path.add(current);
            visited[row][col] = true;

            if (pathCovered[row][col] && !confluenceMarked) {
                confluenceCells.add(current);
                confluenceMarked = true;
            }

            if (waterCells.contains(current)) {
                break;
            }

            CellPos target = findNearestWaterCell(current, waterCells);
            if (target == null) {
                break;
            }

            Direction d8Dir = flowDirection[row][col];
            CellPos d8Next = getNeighborInDirection(regionPos, current, d8Dir);
            int currentDist = distanceSquared(row, col, target.row(), target.col());

            if (d8Dir != Direction.NONE && isValidMove(d8Next, visited, forbidden)) {
                int d8Dist = distanceSquared(d8Next.row(), d8Next.col(), target.row(), target.col());
                if (d8Dist < currentDist) {
                    current = d8Next;
                    continue;
                }
            }

            CellPos closest = findClosestNeighbor(regionPos, current, target, flowDirection, visited, forbidden);
            if (closest == null) {
                break;
            }
            current = closest;
        }

        return path;
    }

    private static List<CellPos> tracePathToOutput(
            RegionPos regionPos,
            Direction[][] flowDirection,
            CellPos start,
            CellPos target,
            boolean[][] pathCovered,
            boolean[][] forbidden,
            List<CellPos> confluenceCells) {

        List<CellPos> path = new ArrayList<>();
        int cellsPerRegion = RiverConfig.CELLS_PER_REGION.get();
        boolean[][] visited = new boolean[cellsPerRegion][cellsPerRegion];
        boolean confluenceMarked = false;

        CellPos current = start;

        while (true) {
            int row = current.row();
            int col = current.col();

            path.add(current);
            visited[row][col] = true;

            if (pathCovered[row][col] && !confluenceMarked) {
                confluenceCells.add(current);
                confluenceMarked = true;
            }

            if (current.equals(target)) {
                break;
            }

            Direction d8Dir = flowDirection[row][col];
            CellPos d8Next = getNeighborInDirection(regionPos, current, d8Dir);
            int currentDist = distanceSquared(row, col, target.row(), target.col());

            if (d8Dir != Direction.NONE && isValidMove(d8Next, visited, forbidden)) {
                int d8Dist = distanceSquared(d8Next.row(), d8Next.col(), target.row(), target.col());
                if (d8Dist < currentDist) {
                    current = d8Next;
                    continue;
                }
            }

            CellPos closest = findClosestNeighbor(regionPos, current, target, flowDirection, visited, forbidden);
            if (closest == null) {
                break;
            }
            current = closest;
        }

        return path;
    }

    private static CellPos getNeighborInDirection(RegionPos regionPos, CellPos pos, Direction dir) {
        int row = pos.row();
        int col = pos.col();
        int newRow = row;
        int newCol = col;

        switch (dir) {
            case NORTH -> newRow = row - 1;
            case SOUTH -> newRow = row + 1;
            case EAST -> newCol = col + 1;
            case WEST -> newCol = col - 1;
            case NORTHEAST -> { newRow = row - 1; newCol = col + 1; }
            case NORTHWEST -> { newRow = row - 1; newCol = col - 1; }
            case SOUTHEAST -> { newRow = row + 1; newCol = col + 1; }
            case SOUTHWEST -> { newRow = row + 1; newCol = col - 1; }
            default -> { newRow = -1; newCol = -1; }
        }

        return CellPos.fromLocal(regionPos, newRow, newCol);
    }

    private static CellPos findClosestNeighbor(
            RegionPos regionPos, CellPos current, CellPos target,
            Direction[][] flowDirection,
            boolean[][] visited, boolean[][] forbidden) {

        int row = current.row();
        int col = current.col();
        CellPos[] neighbors = {
            CellPos.fromLocal(regionPos, row - 1, col),
            CellPos.fromLocal(regionPos, row + 1, col),
            CellPos.fromLocal(regionPos, row, col + 1),
            CellPos.fromLocal(regionPos, row, col - 1)
        };

        int currentDist = distanceSquared(row, col, target.row(), target.col());
        Direction currentD8 = flowDirection[row][col];

        CellPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;

        for (CellPos neighbor : neighbors) {
            if (!isValidMove(neighbor, visited, forbidden)) {
                continue;
            }

            int dist = distanceSquared(neighbor.row(), neighbor.col(), target.row(), target.col());
            if (dist >= currentDist) {
                continue;
            }

            int moveRow = neighbor.row() - row;
            int moveCol = neighbor.col() - col;
            int score = computeMoveAlignment(moveRow, moveCol, currentD8);

            if (score > bestScore || (score == bestScore && dist < bestDist)) {
                bestScore = score;
                bestDist = dist;
                best = neighbor;
            }
        }

        return best;
    }

    private static int computeMoveAlignment(int moveRow, int moveCol, Direction d8Dir) {
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

    private static boolean isValidMove(CellPos pos, boolean[][] visited, boolean[][] forbidden) {
        int row = pos.row();
        int col = pos.col();
        int cellsPerRegion = RiverConfig.CELLS_PER_REGION.get();

        if (row < 0 || row >= cellsPerRegion || col < 0 || col >= cellsPerRegion) {
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
