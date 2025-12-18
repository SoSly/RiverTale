package org.sosly.rivertale.worldgen.river;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;

public class ContinentsDensityProvider implements DensityProvider {

    private static final double OCEAN_THRESHOLD = -0.13;
    private static final int SAMPLE_Y = 63;
    private static final int SUBCELL_SAMPLES = 7;

    private final DensityFunction continentsFunction;

    public ContinentsDensityProvider(ServerLevel level) {
        RandomState randomState = level.getChunkSource().randomState();
        NoiseRouter router = randomState.router();
        this.continentsFunction = router.continents();
    }

    @Override
    public double getDensity(int worldX, int worldZ) {
        DensityFunction.SinglePointContext context =
            new DensityFunction.SinglePointContext(worldX, SAMPLE_Y, worldZ);
        return continentsFunction.compute(context);
    }

    @Override
    public double getAveragedDensity(int worldX, int worldZ, int cellSize) {
        double step = cellSize / (double) SUBCELL_SAMPLES;
        double sum = 0.0;
        int count = 0;

        for (int i = 0; i < SUBCELL_SAMPLES; i++) {
            for (int j = 0; j < SUBCELL_SAMPLES; j++) {
                int sampleX = worldX + (int) (i * step);
                int sampleZ = worldZ + (int) (j * step);
                sum += getDensity(sampleX, sampleZ);
                count++;
            }
        }

        return sum / count;
    }

    @Override
    public double[][] sampleSubcellDensities(int worldX, int worldZ, int cellSize) {
        double step = cellSize / (double) SUBCELL_SAMPLES;
        double[][] densities = new double[SUBCELL_SAMPLES][SUBCELL_SAMPLES];

        for (int i = 0; i < SUBCELL_SAMPLES; i++) {
            for (int j = 0; j < SUBCELL_SAMPLES; j++) {
                int sampleX = worldX + (int) (i * step);
                int sampleZ = worldZ + (int) (j * step);
                densities[i][j] = getDensity(sampleX, sampleZ);
            }
        }

        return densities;
    }

    @Override
    public boolean isOcean(double density) {
        return density < OCEAN_THRESHOLD;
    }
}
