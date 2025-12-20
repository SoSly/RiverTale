package org.sosly.rivertale.worldgen.river;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.config.RiverConfig;

public class RegionDensityProvider implements DensityProvider {

    private static final int SAMPLE_Y = 63;
    private static final int SUBCELL_SAMPLES = 8;

    private final DensityFunction continentsFunction;
    private final DensityFunction depthFunction;

    public RegionDensityProvider(RandomState randomState) {
        NoiseRouter router = randomState.router();
        this.continentsFunction = router.continents();
        this.depthFunction = router.depth();
    }

    @Override
    public double getDensity(int worldX, int worldZ) {
        DensityFunction.SinglePointContext context =
            new DensityFunction.SinglePointContext(worldX, SAMPLE_Y, worldZ);
        double continents = continentsFunction.compute(context);
        double depth = depthFunction.compute(context);
        return continents + (depth * RiverConfig.DEPTH_WEIGHT.get());
    }

    @Override
    public double getAveragedDensity(int worldX, int worldZ, int regionSize) {
        double step = regionSize / (double) SUBCELL_SAMPLES;
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
    public double[][] sampleCellDensities(int worldX, int worldZ, int regionSize) {
        double step = regionSize / (double) SUBCELL_SAMPLES;
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

    public double getContinents(int worldX, int worldZ) {
        DensityFunction.SinglePointContext context =
            new DensityFunction.SinglePointContext(worldX, SAMPLE_Y, worldZ);
        return continentsFunction.compute(context);
    }

    @Override
    public boolean isOcean(int worldX, int worldZ) {
        return getContinents(worldX, worldZ) < RiverConfig.OCEAN_THRESHOLD.get();
    }

    public double getDepth(int worldX, int worldZ) {
        DensityFunction.SinglePointContext context =
            new DensityFunction.SinglePointContext(worldX, SAMPLE_Y, worldZ);
        return depthFunction.compute(context);
    }

    @Override
    public boolean isLake(int worldX, int worldZ) {
        if (isOcean(worldX, worldZ)) {
            return false;
        }
        return getDepth(worldX, worldZ) < RiverConfig.LAKE_THRESHOLD.get();
    }
}
