package org.sosly.rivertale.path;

import java.util.function.BiFunction;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;

public class FlowCalculator {

    private static final double SQRT2 = Math.sqrt(2.0);

    private FlowCalculator() {
    }

    public static Grid compute(
            RegionPos regionPos,
            BiFunction<Integer, Integer, Double> densitySampler) {

        int gridSize = RiverConfig.CELLS_PER_REGION.get();
        int cellSize = CellPos.getCellSize();
        int regionOriginX = regionPos.worldX();
        int regionOriginZ = regionPos.worldZ();

        Grid grid = Grid.create(regionPos);

        for (int row = 0; row < gridSize; row++) {
            for (int col = 0; col < gridSize; col++) {
                int worldX = regionOriginX + col * cellSize + cellSize / 2;
                int worldZ = regionOriginZ + row * cellSize + cellSize / 2;
                Direction dir = findSteepestNeighbor(worldX, worldZ, cellSize, densitySampler);
                grid.get(row, col).setFlowDirection(dir);
            }
        }

        return grid;
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
