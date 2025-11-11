package org.sosly.rivertale.worldgen;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

public class ValleyFillingDensityFunction implements DensityFunction {
    private final DensityFunction wrapped;
    private final DensityFunction continents;
    private final DensityFunction ridges;

    private static final double COASTAL_START = -0.16;
    private static final double COASTAL_END = -0.14;
    private static final double RIVER_THRESHOLD = 0.05;
    private static final double FILL_STRENGTH = 0.5;
    private static final int SEA_LEVEL = 63;
    private static final int MAX_FILL_DEPTH = 10;

    public ValleyFillingDensityFunction(DensityFunction wrapped, DensityFunction continents, DensityFunction ridges) {
        this.wrapped = wrapped;
        this.continents = continents;
        this.ridges = ridges;
    }

    @Override
    public double compute(FunctionContext context) {
        double originalDensity = wrapped.compute(context);

        if (originalDensity >= 0.0) {
            return originalDensity;
        }

        int y = context.blockY();

        if (y > SEA_LEVEL) {
            return originalDensity;
        }

        double continentalness = continents.compute(context);
        if (continentalness < COASTAL_START) {
            return originalDensity;
        }

        double coastalProgress = continentalness > COASTAL_END ? 1.0 : smoothstep(COASTAL_START, COASTAL_END, continentalness);

        double ridgeValue = ridges.compute(context);

        if (Math.abs(ridgeValue) > RIVER_THRESHOLD) {
            return originalDensity;
        }

        double depthFromSeaLevel = SEA_LEVEL - y;
        double depthFactor = depthFromSeaLevel / MAX_FILL_DEPTH;
        if (depthFactor > 1.0) {
            return originalDensity;
        }

        double riverFactor = 1.0 - (Math.abs(ridgeValue) / RIVER_THRESHOLD);

        double fillAmount = depthFactor * coastalProgress * riverFactor * FILL_STRENGTH;

        return originalDensity + fillAmount;
    }

    @Override
    public void fillArray(double[] pArray, ContextProvider pContextProvider) {
        wrapped.fillArray(pArray, pContextProvider);

        for (int i = 0; i < pArray.length; i++) {
            if (pArray[i] >= 0.0) {
                continue;
            }

            FunctionContext context = pContextProvider.forIndex(i);
            pArray[i] = computeWithContext(pArray[i], context);
        }
    }

    private double computeWithContext(double originalDensity, FunctionContext context) {
        int y = context.blockY();

        if (y > SEA_LEVEL) {
            return originalDensity;
        }

        double continentalness = continents.compute(context);
        if (continentalness < COASTAL_START) {
            return originalDensity;
        }

        double coastalProgress = continentalness > COASTAL_END ? 1.0 : smoothstep(COASTAL_START, COASTAL_END, continentalness);

        double ridgeValue = ridges.compute(context);

        if (Math.abs(ridgeValue) > RIVER_THRESHOLD) {
            return originalDensity;
        }

        double depthFromSeaLevel = SEA_LEVEL - y;
        double depthFactor = depthFromSeaLevel / MAX_FILL_DEPTH;
        if (depthFactor > 1.0) {
            return originalDensity;
        }

        double riverFactor = 1.0 - (Math.abs(ridgeValue) / RIVER_THRESHOLD);

        double fillAmount = depthFactor * coastalProgress * riverFactor * FILL_STRENGTH;

        return originalDensity + fillAmount;
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0 - 2.0 * t);
    }

    @Override
    public DensityFunction mapAll(Visitor pVisitor) {
        return new ValleyFillingDensityFunction(
            wrapped.mapAll(pVisitor),
            continents.mapAll(pVisitor),
            ridges.mapAll(pVisitor)
        );
    }

    @Override
    public double minValue() {
        return wrapped.minValue();
    }

    @Override
    public double maxValue() {
        return Math.max(wrapped.maxValue(), wrapped.minValue() + FILL_STRENGTH);
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return wrapped.codec();
    }
}
