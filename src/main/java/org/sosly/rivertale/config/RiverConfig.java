package org.sosly.rivertale.config;

import net.minecraftforge.common.ForgeConfigSpec;

public class RiverConfig {

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.IntValue CELL_SIZE;
    public static final ForgeConfigSpec.DoubleValue PARTICIPATION_RATE;
    public static final ForgeConfigSpec.DoubleValue DENSITY_EQUALITY_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue OCEAN_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue LAKE_THRESHOLD;
    public static final ForgeConfigSpec.DoubleValue DEPTH_WEIGHT;
    public static final ForgeConfigSpec.IntValue UPSTREAM_DEPTH_LIMIT;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("river_cells");

        CELL_SIZE = builder
            .comment("Size of each river cell in blocks. Larger cells improve performance but reduce detail.")
            .defineInRange("cellSize", 256, 128, 4096);

        PARTICIPATION_RATE = builder
            .comment("Probability that a land cell will participate in river generation (0.0 to 1.0).")
            .defineInRange("participationRate", 0.6, 0.0, 1.0);

        DENSITY_EQUALITY_THRESHOLD = builder
            .comment("Maximum density difference for two cells to be considered equal when determining flow direction.")
            .defineInRange("densityEqualityThreshold", 0.01, 0.001, 0.1);

        OCEAN_THRESHOLD = builder
            .comment("Continents density value below which terrain is considered ocean.")
            .defineInRange("oceanThreshold", -0.13, -1.0, 0.0);

        LAKE_THRESHOLD = builder
            .comment("Depth density value below which terrain is considered a lake (if not ocean).")
            .defineInRange("lakeThreshold", 0.0, -1.0, 1.0);

        DEPTH_WEIGHT = builder
            .comment("Weight applied to depth density when calculating combined density for flow direction.")
            .defineInRange("depthWeight", 1.0, 0.0, 10.0);

        UPSTREAM_DEPTH_LIMIT = builder
            .comment("Maximum recursion depth when counting upstream cells for river width calculation.")
            .defineInRange("upstreamDepthLimit", 3, 1, 10);

        builder.pop();

        SPEC = builder.build();
    }
}
