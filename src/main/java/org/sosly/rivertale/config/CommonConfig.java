package org.sosly.rivertale.config;

import net.minecraftforge.common.ForgeConfigSpec;

public record CommonConfig(
    int regionSize,
    int cellSize,
    int samplesPerCell,
    double oceanThreshold,
    int mergeThreshold,
    int minSlope,
    int minPathLength,
    int embankmentRadius,
    boolean enableMetrics
) {
    private static final int DEFAULT_REGION_SIZE = 32;
    private static final int DEFAULT_CELL_SIZE = 3;
    private static final int DEFAULT_SAMPLES_PER_CELL = 1;
    private static final double DEFAULT_OCEAN_THRESHOLD = -0.17;
    private static final int DEFAULT_MERGE_THRESHOLD = 10;
    private static final int DEFAULT_MIN_SLOPE = 1;
    private static final int DEFAULT_MIN_PATH_LENGTH = 3;
    private static final int DEFAULT_EMBANKMENT_RADIUS = 288;
    private static final boolean DEFAULT_ENABLE_METRICS = false;

    private static CommonConfig instance = new CommonConfig(
        DEFAULT_REGION_SIZE, DEFAULT_CELL_SIZE, DEFAULT_SAMPLES_PER_CELL, DEFAULT_OCEAN_THRESHOLD, DEFAULT_MERGE_THRESHOLD, DEFAULT_MIN_SLOPE, DEFAULT_MIN_PATH_LENGTH, DEFAULT_EMBANKMENT_RADIUS, DEFAULT_ENABLE_METRICS);

    public CommonConfig {
        if (cellSize < 1 || cellSize > 8) {
            throw new IllegalArgumentException("cellSize must be 1-8 chunks");
        }
        if (regionSize < 8 || regionSize > 64) {
            throw new IllegalArgumentException("regionSize must be 8-64 cells");
        }
        if (samplesPerCell < 1 || samplesPerCell > cellSize * cellSize) {
            throw new IllegalArgumentException("samplesPerCell must be 1 to cellSize squared");
        }
    }

    public static CommonConfig get() {
        return instance;
    }

    public static void set(CommonConfig config) {
        instance = config;
    }

    public int cellBlocks() {
        return cellSize * 16;
    }

    public int regionBlocks() {
        return regionSize * cellBlocks();
    }

    public int cellsPerRegion() {
        return regionSize;
    }

    public static class Spec {
        public static final ForgeConfigSpec SPEC;
        public static final ForgeConfigSpec.IntValue REGION_SIZE;
        public static final ForgeConfigSpec.IntValue CELL_SIZE;
        public static final ForgeConfigSpec.IntValue SAMPLES_PER_CELL;
        public static final ForgeConfigSpec.DoubleValue OCEAN_THRESHOLD;
        public static final ForgeConfigSpec.IntValue MERGE_THRESHOLD;
        public static final ForgeConfigSpec.IntValue MIN_SLOPE;
        public static final ForgeConfigSpec.IntValue MIN_PATH_LENGTH;
        public static final ForgeConfigSpec.IntValue EMBANKMENT_RADIUS;
        public static final ForgeConfigSpec.BooleanValue ENABLE_METRICS;

        static {
            ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

            REGION_SIZE = builder
                .comment("Cells per side of each region")
                .defineInRange("regionSize", DEFAULT_REGION_SIZE, 8, 64);

            CELL_SIZE = builder
                .comment("Chunks per side of each cell")
                .defineInRange("cellSize", DEFAULT_CELL_SIZE, 1, 8);

            SAMPLES_PER_CELL = builder
                .comment("Chunks sampled per cell (1 = center only)")
                .defineInRange("samplesPerCell", DEFAULT_SAMPLES_PER_CELL, 1, 64);

            OCEAN_THRESHOLD = builder
                .comment("Continents density value below which terrain is considered ocean.")
                .defineInRange("oceanThreshold", DEFAULT_OCEAN_THRESHOLD, -1.0, 0.0);

            MERGE_THRESHOLD = builder
                .comment("Distance in cells from ocean at which rivers prioritize alignment over slope.")
                .defineInRange("mergeThreshold", DEFAULT_MERGE_THRESHOLD, 0, 32);

            MIN_SLOPE = builder
                .comment("Minimum drop in blocks between adjacent river cells. Rivers carve down when terrain rises.")
                .defineInRange("minSlope", DEFAULT_MIN_SLOPE, 0, 10);

            MIN_PATH_LENGTH = builder
                .comment("Minimum number of cells a river path must have to be valid. Shorter paths are pruned.")
                .defineInRange("minPathLength", DEFAULT_MIN_PATH_LENGTH, 1, 20);

            EMBANKMENT_RADIUS = builder
                .comment("Distance in blocks from river channel center where terrain is pulled toward river level.")
                .defineInRange("embankmentRadius", DEFAULT_EMBANKMENT_RADIUS, 16, 512);

            ENABLE_METRICS = builder
                .comment("Enable performance metrics collection. Useful for debugging but consumes memory.")
                .define("enableMetrics", DEFAULT_ENABLE_METRICS);

            SPEC = builder.build();
        }

        public static void load() {
            set(new CommonConfig(
                REGION_SIZE.get(),
                CELL_SIZE.get(),
                SAMPLES_PER_CELL.get(),
                OCEAN_THRESHOLD.get(),
                MERGE_THRESHOLD.get(),
                MIN_SLOPE.get(),
                MIN_PATH_LENGTH.get(),
                EMBANKMENT_RADIUS.get(),
                ENABLE_METRICS.get()
            ));
        }
    }
}
