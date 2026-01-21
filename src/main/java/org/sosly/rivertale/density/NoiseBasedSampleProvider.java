package org.sosly.rivertale.density;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public class NoiseBasedSampleProvider implements SampleProvider {
    private final NoiseRouter router;
    private final int y;

    public static NoiseBasedSampleProvider create(RandomState random, int y) {
        return new NoiseBasedSampleProvider(random, y);
    }

    private NoiseBasedSampleProvider(RandomState random, int y) {
        this.router = random.router();
        this.y = y;
    }

    @Override
    public Sample sampleCore(ChunkPos pos) {
        Timer.Record timer = Store.getTimer(NoiseBasedSampleProvider.class, "sampleCore").start();

        int x = pos.getMiddleBlockX();
        int z = pos.getMiddleBlockZ();

        DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(x, y, z);

        double continents = router.continents().compute(ctx);
        double depth = router.depth().compute(ctx);

        timer.stop();

        return new Sample(pos, continents, depth);
    }

    @Override
    public double sample(DensityField field, ChunkPos pos) {
        int x = pos.getMiddleBlockX();
        int z = pos.getMiddleBlockZ();
        DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(x, y, z);

        return switch (field) {
            case CONTINENTS -> router.continents().compute(ctx);
            case DEPTH -> router.depth().compute(ctx);
            case EROSION -> router.erosion().compute(ctx);
            case RIDGES -> router.ridges().compute(ctx);
            case TEMPERATURE -> router.temperature().compute(ctx);
            case VEGETATION -> router.vegetation().compute(ctx);
        };
    }
}
