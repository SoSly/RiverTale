package org.sosly.rivertale.config;

import java.util.Objects;
import net.minecraftforge.common.ForgeConfigSpec;

public record CommonConfig(
    int regionSize,
    int cellSize,
    int samplesPerCell,
    int mergeThreshold,
    int minSlope,
    int minPathLength,
    int embankmentRadius,
    boolean enableMetrics,
    boolean enableNudging,
    int nudgingLimit,
    int fallbackApproachCells,
    int awayStepAllowance,
    SourceConfig sources,
    CacheConfig caches,
    int flowUnlockMillis,
    boolean localRiverPreview
) {
    private static final int DEFAULT_REGION_SIZE = 24;
    private static final int DEFAULT_CELL_SIZE = 4;
    private static final int DEFAULT_SAMPLES_PER_CELL = 5;
    private static final int DEFAULT_MERGE_THRESHOLD = 10;
    private static final int DEFAULT_MIN_SLOPE = 1;
    private static final int DEFAULT_MIN_PATH_LENGTH = 3;
    private static final int DEFAULT_EMBANKMENT_RADIUS = 288;
    private static final boolean DEFAULT_ENABLE_METRICS = false;
    private static final boolean DEFAULT_ENABLE_NUDGING = true;
    private static final int DEFAULT_NUDGING_LIMIT = 16;
    private static final int DEFAULT_FALLBACK_APPROACH_CELLS = 5;
    private static final int DEFAULT_AWAY_STEP_ALLOWANCE = 1;
    private static final int DEFAULT_FLOW_UNLOCK_MILLIS = 5000;
    private static final boolean DEFAULT_LOCAL_RIVER_PREVIEW = false;

    private static CommonConfig instance = new CommonConfig(
        DEFAULT_REGION_SIZE, DEFAULT_CELL_SIZE, DEFAULT_SAMPLES_PER_CELL, DEFAULT_MERGE_THRESHOLD, DEFAULT_MIN_SLOPE, DEFAULT_MIN_PATH_LENGTH, DEFAULT_EMBANKMENT_RADIUS,
        DEFAULT_ENABLE_METRICS, DEFAULT_ENABLE_NUDGING, DEFAULT_NUDGING_LIMIT, DEFAULT_FALLBACK_APPROACH_CELLS,
        DEFAULT_AWAY_STEP_ALLOWANCE, SourceConfig.DEFAULT, CacheConfig.DEFAULT, DEFAULT_FLOW_UNLOCK_MILLIS, DEFAULT_LOCAL_RIVER_PREVIEW);


    public CommonConfig {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(caches, "caches");
        if (fallbackApproachCells < 0 || awayStepAllowance < 0 || awayStepAllowance == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Routing distances must be nonnegative and awayStepAllowance must leave room for one rejected step");
        }
        if (flowUnlockMillis < 0) {
            throw new IllegalArgumentException("flowUnlockMillis must be nonnegative");
        }
        if (nudgingLimit < 0) {
            throw new IllegalArgumentException("nudgingLimit must be nonnegative");
        }
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
        public static final ForgeConfigSpec.IntValue MERGE_THRESHOLD;
        public static final ForgeConfigSpec.IntValue MIN_SLOPE;
        public static final ForgeConfigSpec.IntValue MIN_PATH_LENGTH;
        public static final ForgeConfigSpec.IntValue EMBANKMENT_RADIUS;
        public static final ForgeConfigSpec.BooleanValue ENABLE_METRICS;
        public static final ForgeConfigSpec.BooleanValue ENABLE_NUDGING;
        public static final ForgeConfigSpec.IntValue NUDGING_LIMIT;
        public static final ForgeConfigSpec.IntValue FALLBACK_APPROACH_CELLS;
        public static final ForgeConfigSpec.IntValue AWAY_STEP_ALLOWANCE;
        public static final ForgeConfigSpec.IntValue FLOW_UNLOCK_MILLIS;
        public static final ForgeConfigSpec.BooleanValue LOCAL_RIVER_PREVIEW;
        private static final SourceConfig.Spec SOURCES;
        private static final CacheConfig.Spec CACHES;

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

            MERGE_THRESHOLD = builder
                .comment("Legacy path implementation only: distance in cells from ocean at which rivers prioritize alignment over slope. Current previews use fallbackApproachCells.")
                .defineInRange("mergeThreshold", DEFAULT_MERGE_THRESHOLD, 0, 32);

            MIN_SLOPE = builder
                .comment("Legacy elevation assignment only: minimum drop in blocks between adjacent river cells. Does not affect current route previews.")
                .defineInRange("minSlope", DEFAULT_MIN_SLOPE, 0, 10);

            MIN_PATH_LENGTH = builder
                .comment("Legacy path pruning only: minimum cells per river path. Current watershed previews do not prune short tributaries.")
                .defineInRange("minPathLength", DEFAULT_MIN_PATH_LENGTH, 1, 20);

            EMBANKMENT_RADIUS = builder
                .comment("Distance in blocks from river channel center where terrain is pulled toward river level.")
                .defineInRange("embankmentRadius", DEFAULT_EMBANKMENT_RADIUS, 16, 512);

            ENABLE_METRICS = builder
                .comment("Enable performance metrics collection. Useful for debugging but consumes memory.")
                .define("enableMetrics", DEFAULT_ENABLE_METRICS);

            ENABLE_NUDGING = builder
                .comment("Allow river path previews to rotate downhill directions toward their destination. Nudged steps may go uphill.")
                .define("enableNudging", DEFAULT_ENABLE_NUDGING);

            NUDGING_LIMIT = builder
                .comment("Maximum uphill rise in blocks per nudged step, measured between sampled average cell heights. Zero allows flat or downhill nudges. Direct approaches are unaffected.")
                .defineInRange("nudgingLimit", DEFAULT_NUDGING_LIMIT, 0, Integer.MAX_VALUE);

            FALLBACK_APPROACH_CELLS = builder
                .comment("Direct fallback radius in cells, after downhill and nudged alternatives fail. Zero disables fallback. Fallback may climb without the nudging limit.")
                .defineInRange("fallbackApproachCells", DEFAULT_FALLBACK_APPROACH_CELLS, 0, Integer.MAX_VALUE);

            AWAY_STEP_ALLOWANCE = builder
                .comment("Maximum consecutive downhill steps that move farther from or equally far from the selected destination. A closer step resets the count. Zero requires every step to get closer. Forward tracing and upstream discovery share this limit.")
                .defineInRange("awayStepAllowance", DEFAULT_AWAY_STEP_ALLOWANCE, 0, Integer.MAX_VALUE - 1);

            LOCAL_RIVER_PREVIEW = builder
                .comment("Diagnostic: trace sources only in the nine visible regions for /rivertale vis. Skip recursive upstream and tributary discovery. Routes can still leave the visible regions; watersheds omit distant sources. Restart after editing.")
                .define("localRiverPreview", DEFAULT_LOCAL_RIVER_PREVIEW);

            FLOW_UNLOCK_MILLIS = builder
                .comment("Milliseconds after a player edit during which the next river-water spread into that block may occur. Zero disables this temporary allowance.")
                .defineInRange("flowUnlockMillis", DEFAULT_FLOW_UNLOCK_MILLIS, 0, Integer.MAX_VALUE);

            builder.comment("Source classification thresholds. Preserve existing comparison boundaries and source precedence. Restart after editing.").push("sources");
            SOURCES = new SourceConfig.Spec(builder);
            builder.pop();

            builder.comment("Cache entry limits. Larger values retain more data and use more memory. Restart after editing.").push("caches");
            CACHES = new CacheConfig.Spec(builder);
            builder.pop();

            SPEC = builder.build();
        }

        public static void load() {
            set(new CommonConfig(
                REGION_SIZE.get(),
                CELL_SIZE.get(),
                SAMPLES_PER_CELL.get(),
                MERGE_THRESHOLD.get(),
                MIN_SLOPE.get(),
                MIN_PATH_LENGTH.get(),
                EMBANKMENT_RADIUS.get(),
                ENABLE_METRICS.get(),
                ENABLE_NUDGING.get(),
                NUDGING_LIMIT.get(),
                FALLBACK_APPROACH_CELLS.get(),
                AWAY_STEP_ALLOWANCE.get(),
                SOURCES.load(),
                CACHES.load(),
                FLOW_UNLOCK_MILLIS.get(),
                LOCAL_RIVER_PREVIEW.get()
            ));
        }
    }
}
