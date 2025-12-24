package org.sosly.rivertale.path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.DensityProvider;

public class Refiner {

    private record PathResult(
        Map<Direction, List<CellPos>> riverPaths,
        List<CellPos> terminusList,
        List<CellPos> confluenceCells
    ) {}


    private record CrossingCandidate(int slot, double[] densities, boolean isOutput) {}

    private record InputCrossing(CellPos cell, Direction direction) {}

    private record InputCollection(List<InputCrossing> inputs, Set<CellPos> forbidden) {}

    private Refiner() {
    }

    public static Flow refine(
            RegionPos regionPos,
            Grid flowGrid,
            RegionType featureRegionType,
            Grid[] neighborFlowGrids,
            RegionType[] neighborFeatureRegionTypes,
            DensityProvider densityProvider) {

        if (featureRegionType == RegionType.BODY) {
            return new Flow(flowGrid, new Crossing[4], new double[4], Direction.NONE,
                List.of(), Map.of(), List.of());
        }

        int cellSize = CellPos.getCellSize();
        int regionOriginX = regionPos.worldX();
        int regionOriginZ = regionPos.worldZ();

        Crossing[] crossings = new Crossing[4];
        double[] crossingStrengths = new double[4];
        findEdgeCrossings(regionPos, regionOriginX, regionOriginZ, cellSize, densityProvider,
            flowGrid, neighborFlowGrids, neighborFeatureRegionTypes,
            crossings, crossingStrengths);

        boolean isShore = featureRegionType == RegionType.SHORE;
        Direction primaryOutputDirection = isShore
            ? Direction.NONE
            : findPrimaryOutput(regionPos, flowGrid, crossings);

        PathResult paths = resolvePaths(regionPos, flowGrid, crossings, primaryOutputDirection,
            isShore, regionOriginX, regionOriginZ, cellSize, densityProvider);

        if (isShore) {
            clearOutputCrossings(regionPos, crossings, crossingStrengths);
        }

        return new Flow(flowGrid, crossings, crossingStrengths, primaryOutputDirection,
            paths.terminusList(), paths.riverPaths(), paths.confluenceCells());
    }

    private static PathResult resolvePaths(
            RegionPos regionPos,
            Grid flowGrid,
            Crossing[] crossings,
            Direction primaryOutputDirection,
            boolean isShore,
            int regionOriginX,
            int regionOriginZ,
            int cellSize,
            DensityProvider densityProvider) {

        Map<Direction, List<CellPos>> riverPaths = new HashMap<>();
        List<CellPos> confluenceCells = new ArrayList<>();
        List<CellPos> terminusList = new ArrayList<>();

        if (isShore) {
            buildShorePaths(regionPos, flowGrid, crossings, regionOriginX, regionOriginZ, cellSize,
                densityProvider, riverPaths, confluenceCells);
            collectPathEndpoints(riverPaths, terminusList);
        } else if (primaryOutputDirection != Direction.NONE) {
            buildPathsToOutput(regionPos, flowGrid, crossings, primaryOutputDirection,
                riverPaths, confluenceCells);
        } else if (!hasAnyOutput(regionPos, crossings)) {
            buildBasinPaths(regionPos, crossings, riverPaths);
            collectPathEndpoints(riverPaths, terminusList);
        }

        return new PathResult(riverPaths, terminusList, confluenceCells);
    }

    private static void findEdgeCrossings(
            RegionPos regionPos,
            int regionOriginX, int regionOriginZ, int cellSize,
            DensityProvider densityProvider,
            Grid flowGrid,
            Grid[] neighborFlowGrids,
            RegionType[] neighborFeatureRegionTypes,
            Crossing[] crossings, double[] crossingStrengths) {

        findCrossing(regionPos, regionOriginX, regionOriginZ, cellSize, densityProvider,
            flowGrid, neighborFlowGrids, neighborFeatureRegionTypes,
            crossings, crossingStrengths, Direction.NORTH);
        findCrossing(regionPos, regionOriginX, regionOriginZ, cellSize, densityProvider,
            flowGrid, neighborFlowGrids, neighborFeatureRegionTypes,
            crossings, crossingStrengths, Direction.SOUTH);
        findCrossing(regionPos, regionOriginX, regionOriginZ, cellSize, densityProvider,
            flowGrid, neighborFlowGrids, neighborFeatureRegionTypes,
            crossings, crossingStrengths, Direction.EAST);
        findCrossing(regionPos, regionOriginX, regionOriginZ, cellSize, densityProvider,
            flowGrid, neighborFlowGrids, neighborFeatureRegionTypes,
            crossings, crossingStrengths, Direction.WEST);
    }

    private static void findCrossing(
            RegionPos regionPos,
            int regionOriginX, int regionOriginZ, int cellSize,
            DensityProvider densityProvider,
            Grid flowGrid,
            Grid[] neighborFlowGrids,
            RegionType[] neighborFeatureRegionTypes,
            Crossing[] crossings, double[] crossingStrengths,
            Direction dir) {

        RegionType neighborRegionType = neighborFeatureRegionTypes[dir.ordinal()];
        boolean neighborIsLand = neighborRegionType != null
            && neighborRegionType != RegionType.BODY
            && neighborRegionType != RegionType.SHORE;
        boolean neighborCanProvideInput = neighborIsLand
            && neighborFlowGrids[dir.ordinal()] != null;

        CrossingCandidate candidate = findBestCrossingSlot(
            regionOriginX, regionOriginZ, cellSize, densityProvider,
            flowGrid, neighborFlowGrids, neighborCanProvideInput, dir);

        if (candidate == null) {
            crossings[dir.ordinal()] = null;
            crossingStrengths[dir.ordinal()] = 0;
            return;
        }

        double diff = Math.abs(candidate.densities()[0] - candidate.densities()[1]);
        RegionPos neighbor = regionPos.relative(dir);
        RegionPos source = candidate.isOutput() ? regionPos : neighbor;
        RegionPos destination = candidate.isOutput() ? neighbor : regionPos;
        crossings[dir.ordinal()] = CrossingStore.getInstance().getOrCreate(source, destination, candidate.slot());
        crossingStrengths[dir.ordinal()] = diff;
    }

    private static CrossingCandidate findBestCrossingSlot(
            int regionOriginX, int regionOriginZ, int cellSize,
            DensityProvider densityProvider,
            Grid flowGrid,
            Grid[] neighborFlowGrids,
            boolean neighborCanProvideInput,
            Direction dir) {

        Direction oppositeDir = dir.opposite();
        Grid neighborGrid = neighborFlowGrids[dir.ordinal()];

        int bestSlot = -1;
        double lowestDensity = Double.MAX_VALUE;
        double[] bestDensities = null;
        boolean bestIsOutput = false;

        for (int edgeSlot = 1; edgeSlot < RiverConfig.CELLS_PER_REGION.get() - 1; edgeSlot++) {
            Direction cellFlow = flowGrid.getOuterEdge(dir, edgeSlot);
            Direction neighborFlow = neighborGrid != null
                ? neighborGrid.getNeighborEdge(dir, edgeSlot)
                : Direction.NONE;

            boolean weFlowOut = cellFlow == dir;
            boolean neighborFlowsIn = neighborCanProvideInput && neighborFlow == oppositeDir;

            if (!weFlowOut && !neighborFlowsIn) {
                continue;
            }

            double[] densities = sampleEdgeDensities(regionOriginX, regionOriginZ, cellSize,
                densityProvider, dir, edgeSlot);
            double avg = (densities[0] + densities[1]) / 2.0;

            if (avg >= lowestDensity) {
                continue;
            }

            lowestDensity = avg;
            bestSlot = edgeSlot;
            bestDensities = densities;
            bestIsOutput = !neighborFlowsIn || (weFlowOut && densities[0] > densities[1]);
        }

        if (bestSlot < 0) {
            return null;
        }

        return new CrossingCandidate(bestSlot, bestDensities, bestIsOutput);
    }

    private static double[] sampleEdgeDensities(
            int regionOriginX, int regionOriginZ, int cellSize,
            DensityProvider densityProvider,
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
            densityProvider.getDensity(ourX, ourZ),
            densityProvider.getDensity(neighborX, neighborZ)
        };
    }

    private static Direction findPrimaryOutput(RegionPos regionPos, Grid flowGrid, Crossing[] crossings) {
        int sumX = 0;
        int sumZ = 0;
        int gridSize = flowGrid.size();

        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                Direction dir = flowGrid.getFlowAt(row, col);
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
            boolean isOutput = crossing != null && crossing.isSource(regionPos);
            if (isOutput && dotProducts[dir] > bestDot) {
                bestDot = dotProducts[dir];
                bestDir = Direction.values()[dir];
            }
        }

        return bestDir;
    }

    private static boolean hasAnyOutput(RegionPos regionPos, Crossing[] crossings) {
        for (Crossing crossing : crossings) {
            if (crossing != null && crossing.isSource(regionPos)) {
                return true;
            }
        }
        return false;
    }

    private static void clearOutputCrossings(RegionPos regionPos, Crossing[] crossings, double[] crossingStrengths) {
        for (int i = 0; i < 4; i++) {
            if (crossings[i] != null && crossings[i].isSource(regionPos)) {
                crossings[i] = null;
                crossingStrengths[i] = 0;
            }
        }
    }

    private static void collectPathEndpoints(Map<Direction, List<CellPos>> riverPaths, List<CellPos> terminusList) {
        for (List<CellPos> path : riverPaths.values()) {
            if (path.isEmpty()) {
                continue;
            }
            terminusList.add(path.get(path.size() - 1));
        }
    }

    private static void buildPathsToOutput(
            RegionPos regionPos,
            Grid flowGrid,
            Crossing[] crossings,
            Direction primaryOutputDirection,
            Map<Direction, List<CellPos>> riverPaths,
            List<CellPos> confluenceCells) {

        Crossing outputCrossing = crossings[primaryOutputDirection.ordinal()];
        if (outputCrossing == null) {
            return;
        }

        CellPos outputCell = outputCrossing.cellFor(regionPos);
        InputCollection collection = collectInputsAndForbidden(regionPos, crossings, primaryOutputDirection);

        if (collection.inputs().isEmpty()) {
            buildDividePaths(regionPos, crossings, riverPaths);
            return;
        }

        Set<CellPos> pathCovered = new HashSet<>();
        for (InputCrossing input : collection.inputs()) {
            List<CellPos> path = findPath(
                regionPos, flowGrid, input.cell(), List.of(outputCell),
                pathCovered, collection.forbidden(), confluenceCells);

            pathCovered.addAll(path);
            riverPaths.put(input.direction(), path);
        }
    }

    private static void buildDividePaths(
            RegionPos regionPos,
            Crossing[] crossings,
            Map<Direction, List<CellPos>> riverPaths) {

        for (int dir = 0; dir < 4; dir++) {
            Crossing crossing = crossings[dir];
            if (crossing == null || !crossing.isSource(regionPos)) {
                continue;
            }

            CellPos cell = crossing.cellFor(regionPos);
            riverPaths.put(Direction.values()[dir], List.of(cell));
        }
    }

    private static InputCollection collectInputsAndForbidden(
            RegionPos regionPos,
            Crossing[] crossings,
            Direction primaryOutputDirection) {

        List<InputCrossing> inputs = new ArrayList<>();
        Set<CellPos> forbidden = new HashSet<>();

        for (int dir = 0; dir < 4; dir++) {
            Crossing crossing = crossings[dir];
            if (crossing == null) {
                continue;
            }

            CellPos cell = crossing.cellFor(regionPos);

            if (crossing.isDestination(regionPos)) {
                inputs.add(new InputCrossing(cell, Direction.values()[dir]));
                continue;
            }

            if (dir != primaryOutputDirection.ordinal()) {
                forbidden.add(cell);
            }
        }

        return new InputCollection(inputs, forbidden);
    }

    private static List<InputCrossing> collectInputCrossings(RegionPos regionPos, Crossing[] crossings) {
        List<InputCrossing> inputs = new ArrayList<>();

        for (int dir = 0; dir < 4; dir++) {
            Crossing crossing = crossings[dir];
            if (crossing == null) {
                continue;
            }
            if (!crossing.isDestination(regionPos)) {
                continue;
            }

            CellPos cell = crossing.cellFor(regionPos);
            inputs.add(new InputCrossing(cell, Direction.values()[dir]));
        }

        return inputs;
    }

    private static void buildBasinPaths(
            RegionPos regionPos,
            Crossing[] crossings,
            Map<Direction, List<CellPos>> riverPaths) {

        for (InputCrossing input : collectInputCrossings(regionPos, crossings)) {
            riverPaths.put(input.direction(), List.of(input.cell()));
        }
    }

    private static void buildShorePaths(
            RegionPos regionPos,
            Grid flowGrid,
            Crossing[] crossings,
            int regionOriginX,
            int regionOriginZ,
            int cellSize,
            DensityProvider densityProvider,
            Map<Direction, List<CellPos>> riverPaths,
            List<CellPos> confluenceCells) {

        List<CellPos> waterCells = findWaterCells(
            regionPos, flowGrid.size(), regionOriginX, regionOriginZ, cellSize, densityProvider);

        if (waterCells.isEmpty()) {
            return;
        }

        Set<CellPos> pathCovered = new HashSet<>();
        Set<CellPos> forbidden = Set.of();

        for (InputCrossing input : collectInputCrossings(regionPos, crossings)) {
            List<CellPos> path = findPath(
                regionPos, flowGrid, input.cell(), waterCells,
                pathCovered, forbidden, confluenceCells);

            pathCovered.addAll(path);
            riverPaths.put(input.direction(), path);
        }
    }

    private static List<CellPos> findWaterCells(
            RegionPos regionPos,
            int cellsPerRegion,
            int regionOriginX,
            int regionOriginZ,
            int cellSize,
            DensityProvider densityProvider) {

        List<CellPos> oceanCells = new ArrayList<>();
        List<CellPos> lakeCells = new ArrayList<>();

        for (int row = 0; row < cellsPerRegion; row++) {
            for (int col = 0; col < cellsPerRegion; col++) {
                int worldX = regionOriginX + col * cellSize + cellSize / 2;
                int worldZ = regionOriginZ + row * cellSize + cellSize / 2;

                if (densityProvider.isOcean(worldX, worldZ)) {
                    oceanCells.add(CellPos.fromLocal(regionPos, row, col));
                } else if (densityProvider.isLake(worldX, worldZ)) {
                    lakeCells.add(CellPos.fromLocal(regionPos, row, col));
                }
            }
        }

        return oceanCells.isEmpty() ? lakeCells : oceanCells;
    }

    private static CellPos findNearestCell(CellPos from, List<CellPos> targets) {
        CellPos nearest = null;
        int minDist = Integer.MAX_VALUE;

        for (CellPos target : targets) {
            int dist = distanceSquared(from, target);
            if (dist < minDist) {
                minDist = dist;
                nearest = target;
            }
        }

        return nearest;
    }

    private static List<CellPos> findPath(
            RegionPos regionPos,
            Grid flowGrid,
            CellPos start,
            List<CellPos> targets,
            Set<CellPos> pathCovered,
            Set<CellPos> forbidden,
            List<CellPos> confluenceCells) {

        List<CellPos> path = new ArrayList<>();
        Set<CellPos> visited = new HashSet<>();
        boolean confluenceMarked = false;
        CellPos current = start;

        while (true) {
            path.add(current);
            visited.add(current);

            if (pathCovered.contains(current)) {
                if (!confluenceMarked) {
                    confluenceCells.add(current);
                }
                break;
            }

            if (targets.contains(current)) {
                break;
            }

            CellPos target = findNearestCell(current, targets);
            if (target == null) {
                break;
            }

            Direction flowDirection = flowGrid.getFlowAt(current.row(), current.col());
            CellPos flowNeighbor = getNeighborInDirection(regionPos, current, flowDirection, flowGrid.size());
            int distanceToTarget = distanceSquared(current, target);

            boolean canFollowFlow = flowNeighbor != null
                && isValidMove(flowNeighbor, flowGrid.size(), visited, forbidden)
                && distanceSquared(flowNeighbor, target) < distanceToTarget;

            if (canFollowFlow) {
                current = flowNeighbor;
                continue;
            }

            CellPos closest = findClosestNeighbor(regionPos, current, target, flowGrid, visited, forbidden);
            if (closest == null) {
                break;
            }
            current = closest;
        }

        return path;
    }

    private static CellPos getNeighborInDirection(RegionPos regionPos, CellPos pos, Direction dir, int gridSize) {
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
            default -> { return null; }
        }

        if (newRow < 0 || newRow >= gridSize || newCol < 0 || newCol >= gridSize) {
            return null;
        }

        return CellPos.fromLocal(regionPos, newRow, newCol);
    }

    private static CellPos findClosestNeighbor(
            RegionPos regionPos, CellPos current, CellPos target,
            Grid flowGrid,
            Set<CellPos> visited, Set<CellPos> forbidden) {

        int row = current.row();
        int col = current.col();
        int gridSize = flowGrid.size();

        List<CellPos> neighbors = new ArrayList<>();
        if (row > 0) {
            neighbors.add(CellPos.fromLocal(regionPos, row - 1, col));
        }
        if (row < gridSize - 1) {
            neighbors.add(CellPos.fromLocal(regionPos, row + 1, col));
        }
        if (col < gridSize - 1) {
            neighbors.add(CellPos.fromLocal(regionPos, row, col + 1));
        }
        if (col > 0) {
            neighbors.add(CellPos.fromLocal(regionPos, row, col - 1));
        }

        int currentDist = distanceSquared(current, target);
        Direction currentD8 = flowGrid.getFlowAt(row, col);

        CellPos best = null;
        int bestScore = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;

        for (CellPos neighbor : neighbors) {
            if (!isValidMove(neighbor, flowGrid.size(), visited, forbidden)) {
                continue;
            }

            int dist = distanceSquared(neighbor, target);
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

    private static boolean isValidMove(CellPos pos, int gridSize, Set<CellPos> visited, Set<CellPos> forbidden) {
        int row = pos.row();
        int col = pos.col();

        if (row < 0 || row >= gridSize || col < 0 || col >= gridSize) {
            return false;
        }
        if (visited.contains(pos)) {
            return false;
        }
        if (forbidden.contains(pos)) {
            return false;
        }
        return true;
    }

    private static int distanceSquared(CellPos a, CellPos b) {
        int dr = a.row() - b.row();
        int dc = a.col() - b.col();
        return dr * dr + dc * dc;
    }
}
