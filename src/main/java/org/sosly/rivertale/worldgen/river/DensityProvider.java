package org.sosly.rivertale.worldgen.river;

public interface DensityProvider {

    double getDensity(int worldX, int worldZ);

    double getAveragedDensity(int worldX, int worldZ, int regionSize);

    double[][] sampleCellDensities(int worldX, int worldZ, int regionSize);

    boolean isOcean(int worldX, int worldZ);

    boolean isLake(int worldX, int worldZ);
}
