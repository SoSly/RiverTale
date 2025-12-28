package org.sosly.rivertale.config;

import net.minecraftforge.common.ForgeConfigSpec;

public record CommonConfig(
    int regionSize,
    int cellSize,
    double oceanThreshold,
    int mergeThreshold,
    int minSlope
) {
    private static final int DEFAULT_REGION_SIZE = 1536;
    private static final int DEFAULT_CELL_SIZE = 48;
    private static final double DEFAULT_OCEAN_THRESHOLD = -0.17;
    private static final int DEFAULT_MERGE_THRESHOLD = 10;
    private static final int DEFAULT_MIN_SLOPE = 1;
    public static final int SEA_LEVEL = 63;

    private static CommonConfig instance = new CommonConfig(
        DEFAULT_REGION_SIZE, DEFAULT_CELL_SIZE, DEFAULT_OCEAN_THRESHOLD, DEFAULT_MERGE_THRESHOLD, DEFAULT_MIN_SLOPE);

    public CommonConfig {
        if (regionSize % 16 != 0) {
            throw new IllegalArgumentException("regionSize must be divisible by 16");
        }
        if (cellSize % 16 != 0) {
            throw new IllegalArgumentException("cellSize must be divisible by 16");
        }
        if (regionSize % cellSize != 0) {
            throw new IllegalArgumentException("regionSize must be divisible by cellSize");
        }
    }

    public static CommonConfig get() {
        return instance;
    }

    public static void set(CommonConfig config) {
        instance = config;
    }

    public int cellsPerRegion() {
        return regionSize / cellSize;
    }

    public static class Spec {
        public static final ForgeConfigSpec SPEC;
        public static final ForgeConfigSpec.IntValue REGION_SIZE;
        public static final ForgeConfigSpec.IntValue CELL_SIZE;
        public static final ForgeConfigSpec.DoubleValue OCEAN_THRESHOLD;
        public static final ForgeConfigSpec.IntValue MERGE_THRESHOLD;
        public static final ForgeConfigSpec.IntValue MIN_SLOPE;

        static {
            ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

            REGION_SIZE = builder
                .comment("Size of each river region in blocks.")
                .defineInRange("regionSize", DEFAULT_REGION_SIZE, 512, 4096);

            CELL_SIZE = builder
                .comment("Size of each cell in blocks.")
                .defineInRange("cellSize", DEFAULT_CELL_SIZE, 16, 128);

            OCEAN_THRESHOLD = builder
                .comment("Continents density value below which terrain is considered ocean.")
                .defineInRange("oceanThreshold", DEFAULT_OCEAN_THRESHOLD, -1.0, 0.0);

            MERGE_THRESHOLD = builder
                .comment("Distance in cells from ocean at which rivers prioritize alignment over slope.")
                .defineInRange("mergeThreshold", DEFAULT_MERGE_THRESHOLD, 0, 32);

            MIN_SLOPE = builder
                .comment("Minimum drop in blocks between adjacent river cells. Rivers carve down when terrain rises.")
                .defineInRange("minSlope", DEFAULT_MIN_SLOPE, 0, 10);

            SPEC = builder.build();
        }

        public static void load() {
            set(new CommonConfig(
                REGION_SIZE.get(),
                CELL_SIZE.get(),
                OCEAN_THRESHOLD.get(),
                MERGE_THRESHOLD.get(),
                MIN_SLOPE.get()
            ));
        }
    }
}
