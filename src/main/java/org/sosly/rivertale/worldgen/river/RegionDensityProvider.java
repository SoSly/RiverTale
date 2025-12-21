package org.sosly.rivertale.worldgen.river;

import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.config.RiverConfig;

public class RegionDensityProvider implements DensityProvider {

    private static final int SAMPLE_Y = 63;
    private static final int CELL_SAMPLES = 8;

    private static final CellSampleDensityCache CONTINENTS_CACHE = new CellSampleDensityCache();
    private static final CellSampleDensityCache DEPTH_CACHE = new CellSampleDensityCache();

    private final DensityFunction continentsFunction;
    private final DensityFunction depthFunction;

    public RegionDensityProvider(RandomState randomState) {
        NoiseRouter router = randomState.router();
        this.continentsFunction = router.continents();
        this.depthFunction = router.depth();
    }

    public static void clearCaches() {
        CONTINENTS_CACHE.clear();
        DEPTH_CACHE.clear();
    }

    @Override
    public double getDensity(int worldX, int worldZ) {
        double continents = getContinents(worldX, worldZ);
        double depth = getDepth(worldX, worldZ);
        return continents + (depth * RiverConfig.DEPTH_WEIGHT.get());
    }

    @Override
    public double getAveragedDensity(int worldX, int worldZ, int regionSize) {
        double step = regionSize / (double) CELL_SAMPLES;
        double sum = 0.0;
        int count = 0;

        for (int i = 0; i < CELL_SAMPLES; i++) {
            for (int j = 0; j < CELL_SAMPLES; j++) {
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
        double step = regionSize / (double) CELL_SAMPLES;
        double[][] densities = new double[CELL_SAMPLES][CELL_SAMPLES];

        for (int i = 0; i < CELL_SAMPLES; i++) {
            for (int j = 0; j < CELL_SAMPLES; j++) {
                int sampleX = worldX + (int) (i * step);
                int sampleZ = worldZ + (int) (j * step);
                densities[i][j] = getDensity(sampleX, sampleZ);
            }
        }

        return densities;
    }

    public double getContinents(int worldX, int worldZ) {
        return CONTINENTS_CACHE.getOrCompute(worldX, worldZ, (x, z) -> {
            DensityFunction.SinglePointContext context =
                new DensityFunction.SinglePointContext(x, SAMPLE_Y, z);
            return continentsFunction.compute(context);
        });
    }

    @Override
    public boolean isOcean(int worldX, int worldZ) {
        return getContinents(worldX, worldZ) < RiverConfig.OCEAN_THRESHOLD.get();
    }

    public double getDepth(int worldX, int worldZ) {
        return DEPTH_CACHE.getOrCompute(worldX, worldZ, (x, z) -> {
            DensityFunction.SinglePointContext context =
                new DensityFunction.SinglePointContext(x, SAMPLE_Y, z);
            return depthFunction.compute(context);
        });
    }

    @Override
    public boolean isLake(int worldX, int worldZ) {
        if (isOcean(worldX, worldZ)) {
            return false;
        }
        return getDepth(worldX, worldZ) < RiverConfig.LAKE_THRESHOLD.get();
    }
}
