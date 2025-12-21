package org.sosly.rivertale.worldgen.river;

import java.util.function.BiFunction;

public class D8FlowCalculator {

    private static final double SQRT2 = Math.sqrt(2.0);

    public static final int GRID_SIZE = 8;

    private D8FlowCalculator() {
    }

    public static FlowDirection[][] computeFlowDirections(
            RiverRegionKey cellKey,
            BiFunction<Integer, Integer, Double> densitySampler) {

        int cellSize = RiverRegionKey.getRegionSize();
        int cellSpacing = cellSize / GRID_SIZE;
        int cellOriginX = cellKey.worldX();
        int cellOriginZ = cellKey.worldZ();

        FlowDirection[][] flowDirection = new FlowDirection[GRID_SIZE][GRID_SIZE];

        for (int row = 0; row < GRID_SIZE; row++) {
            for (int col = 0; col < GRID_SIZE; col++) {
                int worldX = cellOriginX + col * cellSpacing + cellSpacing / 2;
                int worldZ = cellOriginZ + row * cellSpacing + cellSpacing / 2;
                flowDirection[row][col] = findSteepestNeighbor(
                    worldX, worldZ, cellSpacing, densitySampler);
            }
        }

        return flowDirection;
    }

    public static RegionClassification classifyRegion(
            RiverRegionKey cellKey,
            BiFunction<Integer, Integer, Double> continentsSampler,
            BiFunction<Integer, Integer, Double> depthSampler,
            double oceanThreshold,
            double lakeThreshold) {

        int cellSize = RiverRegionKey.getRegionSize();
        double step = cellSize / 8.0;

        boolean hasWater = false;
        boolean hasLand = false;

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int sampleX = cellKey.worldX() + (int) (i * step);
                int sampleZ = cellKey.worldZ() + (int) (j * step);

                double continents = continentsSampler.apply(sampleX, sampleZ);
                if (continents < oceanThreshold) {
                    hasWater = true;
                } else {
                    double depth = depthSampler.apply(sampleX, sampleZ);
                    if (depth < lakeThreshold) {
                        hasWater = true;
                    } else {
                        hasLand = true;
                    }
                }
            }
        }

        if (hasWater && hasLand) {
            return RegionClassification.SHORE;
        }
        if (hasWater) {
            return RegionClassification.BODY;
        }

        return RegionClassification.LAND;
    }

    public static FlowDirection getNeighborEdgeD8(FlowDirection[][] neighborFlowDirections, PathDirection dirFromUs, int pos) {
        return switch (dirFromUs) {
            case NORTH -> neighborFlowDirections[GRID_SIZE - 1][pos];
            case SOUTH -> neighborFlowDirections[0][pos];
            case EAST -> neighborFlowDirections[pos][0];
            case WEST -> neighborFlowDirections[pos][GRID_SIZE - 1];
            default -> FlowDirection.SINK;
        };
    }

    public static FlowDirection getOurEdgeD8(FlowDirection[][] flowDirection, PathDirection dir, int pos) {
        return switch (dir) {
            case NORTH -> flowDirection[0][pos];
            case SOUTH -> flowDirection[GRID_SIZE - 1][pos];
            case EAST -> flowDirection[pos][GRID_SIZE - 1];
            case WEST -> flowDirection[pos][0];
            default -> FlowDirection.SINK;
        };
    }

    public static FlowDirection getOppositeDirection(FlowDirection dir) {
        return switch (dir) {
            case NORTH -> FlowDirection.SOUTH;
            case SOUTH -> FlowDirection.NORTH;
            case EAST -> FlowDirection.WEST;
            case WEST -> FlowDirection.EAST;
            case NORTHEAST -> FlowDirection.SOUTHWEST;
            case NORTHWEST -> FlowDirection.SOUTHEAST;
            case SOUTHEAST -> FlowDirection.NORTHWEST;
            case SOUTHWEST -> FlowDirection.NORTHEAST;
            default -> FlowDirection.SINK;
        };
    }

    private static FlowDirection findSteepestNeighbor(
            int worldX,
            int worldZ,
            int cellSpacing,
            BiFunction<Integer, Integer, Double> densitySampler) {

        double currentDensity = densitySampler.apply(worldX, worldZ);
        double steepestSlope = 0;
        FlowDirection steepestDirection = FlowDirection.SINK;

        double northDensity = densitySampler.apply(worldX, worldZ - cellSpacing);
        double northSlope = currentDensity - northDensity;
        if (northSlope > steepestSlope) {
            steepestSlope = northSlope;
            steepestDirection = FlowDirection.NORTH;
        }

        double southDensity = densitySampler.apply(worldX, worldZ + cellSpacing);
        double southSlope = currentDensity - southDensity;
        if (southSlope > steepestSlope) {
            steepestSlope = southSlope;
            steepestDirection = FlowDirection.SOUTH;
        }

        double eastDensity = densitySampler.apply(worldX + cellSpacing, worldZ);
        double eastSlope = currentDensity - eastDensity;
        if (eastSlope > steepestSlope) {
            steepestSlope = eastSlope;
            steepestDirection = FlowDirection.EAST;
        }

        double westDensity = densitySampler.apply(worldX - cellSpacing, worldZ);
        double westSlope = currentDensity - westDensity;
        if (westSlope > steepestSlope) {
            steepestSlope = westSlope;
            steepestDirection = FlowDirection.WEST;
        }

        double neDensity = densitySampler.apply(worldX + cellSpacing, worldZ - cellSpacing);
        double neSlope = (currentDensity - neDensity) / SQRT2;
        if (neSlope > steepestSlope) {
            steepestSlope = neSlope;
            steepestDirection = FlowDirection.NORTHEAST;
        }

        double nwDensity = densitySampler.apply(worldX - cellSpacing, worldZ - cellSpacing);
        double nwSlope = (currentDensity - nwDensity) / SQRT2;
        if (nwSlope > steepestSlope) {
            steepestSlope = nwSlope;
            steepestDirection = FlowDirection.NORTHWEST;
        }

        double seDensity = densitySampler.apply(worldX + cellSpacing, worldZ + cellSpacing);
        double seSlope = (currentDensity - seDensity) / SQRT2;
        if (seSlope > steepestSlope) {
            steepestSlope = seSlope;
            steepestDirection = FlowDirection.SOUTHEAST;
        }

        double swDensity = densitySampler.apply(worldX - cellSpacing, worldZ + cellSpacing);
        double swSlope = (currentDensity - swDensity) / SQRT2;
        if (swSlope > steepestSlope) {
            steepestDirection = FlowDirection.SOUTHWEST;
        }

        return steepestDirection;
    }
}
