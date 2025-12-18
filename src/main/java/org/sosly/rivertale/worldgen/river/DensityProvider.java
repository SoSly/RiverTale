package org.sosly.rivertale.worldgen.river;

public interface DensityProvider {

    double getDensity(int worldX, int worldZ);

    double getAveragedDensity(int worldX, int worldZ, int cellSize);

    double[][] sampleSubcellDensities(int worldX, int worldZ, int cellSize);

    boolean isOcean(int worldX, int worldZ);

    boolean isLake(int worldX, int worldZ);
}
