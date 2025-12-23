package org.sosly.rivertale.path;

import java.util.function.BiFunction;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;

public class FlowCalculator {

    private static final double SQRT2 = Math.sqrt(2.0);

    public static final int GRID_SIZE = 8;

    private FlowCalculator() {
    }

    public static Direction[][] compute(
            RegionPos regionPos,
            BiFunction<Integer, Integer, Double> densitySampler) {

        int regionSize = RegionPos.getRegionSize();
        int cellSize = regionSize / GRID_SIZE;
        int regionOriginX = regionPos.worldX();
        int regionOriginZ = regionPos.worldZ();

        Direction[][] flowDirection = new Direction[GRID_SIZE][GRID_SIZE];

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int worldX = regionOriginX + col * cellSize + cellSize / 2;
                int worldZ = regionOriginZ + row * cellSize + cellSize / 2;
                flowDirection[row][col] = findSteepestNeighbor(
                    worldX, worldZ, cellSize, densitySampler);
            }
        }

        return flowDirection;
    }

    public static Direction getNeighborEdge(Direction[][] neighborFlowDirections, Direction dirFromUs, int pos) {
        return switch (dirFromUs) {
            case NORTH -> neighborFlowDirections[GRID_SIZE - 1][pos];
            case SOUTH -> neighborFlowDirections[0][pos];
            case EAST -> neighborFlowDirections[pos][0];
            case WEST -> neighborFlowDirections[pos][GRID_SIZE - 1];
            default -> Direction.NONE;
        };
    }

    public static Direction getOuterEdge(Direction[][] flowDirection, Direction dir, int pos) {
        return switch (dir) {
            case NORTH -> flowDirection[0][pos];
            case SOUTH -> flowDirection[GRID_SIZE - 1][pos];
            case EAST -> flowDirection[pos][GRID_SIZE - 1];
            case WEST -> flowDirection[pos][0];
            default -> Direction.NONE;
        };
    }

    private static Direction findSteepestNeighbor(
            int worldX,
            int worldZ,
            int cellSize,
            BiFunction<Integer, Integer, Double> densitySampler) {

        double currentDensity = densitySampler.apply(worldX, worldZ);
        double steepestSlope = 0;
        Direction steepestDirection = Direction.NONE;

        double northDensity = densitySampler.apply(worldX, worldZ - cellSize);
        double northSlope = currentDensity - northDensity;
        if (northSlope > steepestSlope) {
            steepestSlope = northSlope;
            steepestDirection = Direction.NORTH;
        }

        double southDensity = densitySampler.apply(worldX, worldZ + cellSize);
        double southSlope = currentDensity - southDensity;
        if (southSlope > steepestSlope) {
            steepestSlope = southSlope;
            steepestDirection = Direction.SOUTH;
        }

        double eastDensity = densitySampler.apply(worldX + cellSize, worldZ);
        double eastSlope = currentDensity - eastDensity;
        if (eastSlope > steepestSlope) {
            steepestSlope = eastSlope;
            steepestDirection = Direction.EAST;
        }

        double westDensity = densitySampler.apply(worldX - cellSize, worldZ);
        double westSlope = currentDensity - westDensity;
        if (westSlope > steepestSlope) {
            steepestSlope = westSlope;
            steepestDirection = Direction.WEST;
        }

        double neDensity = densitySampler.apply(worldX + cellSize, worldZ - cellSize);
        double neSlope = (currentDensity - neDensity) / SQRT2;
        if (neSlope > steepestSlope) {
            steepestSlope = neSlope;
            steepestDirection = Direction.NORTHEAST;
        }

        double nwDensity = densitySampler.apply(worldX - cellSize, worldZ - cellSize);
        double nwSlope = (currentDensity - nwDensity) / SQRT2;
        if (nwSlope > steepestSlope) {
            steepestSlope = nwSlope;
            steepestDirection = Direction.NORTHWEST;
        }

        double seDensity = densitySampler.apply(worldX + cellSize, worldZ + cellSize);
        double seSlope = (currentDensity - seDensity) / SQRT2;
        if (seSlope > steepestSlope) {
            steepestSlope = seSlope;
            steepestDirection = Direction.SOUTHEAST;
        }

        double swDensity = densitySampler.apply(worldX - cellSize, worldZ + cellSize);
        double swSlope = (currentDensity - swDensity) / SQRT2;
        if (swSlope > steepestSlope) {
            steepestDirection = Direction.SOUTHWEST;
        }

        return steepestDirection;
    }
}
