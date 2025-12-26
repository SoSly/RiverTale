package org.sosly.rivertale.config;

import net.minecraftforge.common.ForgeConfigSpec;

public record CommonConfig(
    int regionSize,
    int cellSize,
    double oceanThreshold
) {
    private static final int DEFAULT_REGION_SIZE = 2048;
    private static final int DEFAULT_CELL_SIZE = 64;
    private static final double DEFAULT_OCEAN_THRESHOLD = -0.164;

    private static CommonConfig instance = new CommonConfig(
        DEFAULT_REGION_SIZE, DEFAULT_CELL_SIZE, DEFAULT_OCEAN_THRESHOLD);

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

            SPEC = builder.build();
        }

        public static void load() {
            set(new CommonConfig(
                REGION_SIZE.get(),
                CELL_SIZE.get(),
                OCEAN_THRESHOLD.get()
            ));
        }
    }
}
