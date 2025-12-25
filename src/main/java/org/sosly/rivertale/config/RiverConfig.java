package org.sosly.rivertale.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class RiverConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue CELLS_PER_REGION;
    public static final ForgeConfigSpec.IntValue REGION_SIZE;
    public static final ForgeConfigSpec.DoubleValue PARTICIPATION_RATE;
    public static final ForgeConfigSpec.DoubleValue OCEAN_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue LAKE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue DEPTH_WEIGHT;
    public static final ForgeConfigSpec.IntValue UPSTREAM_DEPTH_LIMIT;
    public static final ForgeConfigSpec.IntValue MINIMUM_RIVER_LENGTH;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        CELLS_PER_REGION = builder
            .comment("The number of cells per region dimension. Total cells = this value squared.")
            .defineInRange("cellsPerRegion", 32, 8, 64);

        REGION_SIZE = builder
            .comment("Size of each river region in blocks. Larger regions improve performance but reduce detail.")
            .defineInRange("regionSize", 2048, 512, 4096);

        PARTICIPATION_RATE = builder
            .comment("Probability that a land cell will participate in river generation (0.0 to 1.0).")
            .defineInRange("participationRate", 0.7, 0.0, 1.0);

        OCEAN_THRESHOLD = builder
            .comment("Continents density value below which terrain is considered ocean.")
            .defineInRange("oceanThreshold", -0.16, -1.0, 0.0);

        LAKE_THRESHOLD = builder
            .comment("Depth density value below which terrain is considered a lake (if not ocean).")
            .defineInRange("lakeThreshold", -0.06, -1.0, 1.0);

        DEPTH_WEIGHT = builder
            .comment("Weight applied to depth density when calculating combined density for flow direction.")
            .defineInRange("depthWeight", 1.0, 0.0, 10.0);

        UPSTREAM_DEPTH_LIMIT = builder
            .comment("Maximum recursion depth when counting upstream cells for river width calculation.")
            .defineInRange("upstreamDepthLimit", 3, 1, 10);

        MINIMUM_RIVER_LENGTH = builder
            .comment("Minimum total river length in cells. Rivers shorter than this are evicted.")
            .defineInRange("minimumRiverLength", 3, 1, 64);

        SPEC = builder.build();
    }
}
