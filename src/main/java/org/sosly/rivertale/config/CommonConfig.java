package org.sosly.rivertale.config;

import java.util.Objects;
import net.minecraftforge.common.ForgeConfigSpec;

public record CommonConfig(
    int regionSize,
    int cellSize,
    int samplesPerCell,
    boolean enableMetrics,
    boolean enableNudging,
    int nudgingLimit,
    int fallbackApproachCells,
    int awayStepAllowance,
    double riseRunRatio,
    double sourceFlow,
    double flowPerCell,
    SourceConfig sources,
    CacheConfig caches
) {
    private static final int DEFAULT_REGION_SIZE = 24;
    private static final int DEFAULT_CELL_SIZE = 4;
    private static final int DEFAULT_SAMPLES_PER_CELL = 5;
    private static final boolean DEFAULT_ENABLE_METRICS = false;
    private static final boolean DEFAULT_ENABLE_NUDGING = true;
    private static final int DEFAULT_NUDGING_LIMIT = 16;
    private static final int DEFAULT_FALLBACK_APPROACH_CELLS = 5;
    private static final int DEFAULT_AWAY_STEP_ALLOWANCE = 1;
    private static final double DEFAULT_RISE_RUN_RATIO = 0.25;
    private static final double DEFAULT_SOURCE_FLOW = 1.0;
    private static final double DEFAULT_FLOW_PER_CELL = 0.5;

    private static CommonConfig instance = new CommonConfig(
        DEFAULT_REGION_SIZE, DEFAULT_CELL_SIZE, DEFAULT_SAMPLES_PER_CELL,
        DEFAULT_ENABLE_METRICS, DEFAULT_ENABLE_NUDGING, DEFAULT_NUDGING_LIMIT, DEFAULT_FALLBACK_APPROACH_CELLS,
        DEFAULT_AWAY_STEP_ALLOWANCE, DEFAULT_RISE_RUN_RATIO, DEFAULT_SOURCE_FLOW, DEFAULT_FLOW_PER_CELL,
        SourceConfig.DEFAULT, CacheConfig.DEFAULT);

    public CommonConfig(int regionSize, int cellSize, int samplesPerCell, boolean enableMetrics, boolean enableNudging,
                        int nudgingLimit, int fallbackApproachCells, int awayStepAllowance, SourceConfig sources,
                        CacheConfig caches) {
        this(regionSize, cellSize, samplesPerCell, enableMetrics, enableNudging, nudgingLimit, fallbackApproachCells,
            awayStepAllowance, DEFAULT_RISE_RUN_RATIO, sources, caches);
    }

    public CommonConfig(int regionSize, int cellSize, int samplesPerCell, boolean enableMetrics, boolean enableNudging,
                        int nudgingLimit, int fallbackApproachCells, int awayStepAllowance, double riseRunRatio,
                        SourceConfig sources, CacheConfig caches) {
        this(regionSize, cellSize, samplesPerCell, enableMetrics, enableNudging, nudgingLimit, fallbackApproachCells,
            awayStepAllowance, riseRunRatio, DEFAULT_SOURCE_FLOW, DEFAULT_FLOW_PER_CELL, sources, caches);
    }

    public CommonConfig {
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(caches, "caches");
        if (fallbackApproachCells < 0 || awayStepAllowance < 0 || awayStepAllowance == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Routing distances must be nonnegative and awayStepAllowance must leave room for one rejected step");
        }
        if (nudgingLimit < 0) {
            throw new IllegalArgumentException("nudgingLimit must be nonnegative");
        }
        if (!Double.isFinite(riseRunRatio) || riseRunRatio < 0) {
            throw new IllegalArgumentException("riseRunRatio must be finite and nonnegative");
        }
        if (!Double.isFinite(sourceFlow) || sourceFlow < 0) {
            throw new IllegalArgumentException("sourceFlow must be finite and nonnegative");
        }
        if (!Double.isFinite(flowPerCell) || flowPerCell < 0) {
            throw new IllegalArgumentException("flowPerCell must be finite and nonnegative");
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
        public static final ForgeConfigSpec.BooleanValue DISABLE_CHUNK_SAVING;
        public static final ForgeConfigSpec.BooleanValue ENABLE_METRICS;
        public static final ForgeConfigSpec.BooleanValue ENABLE_NUDGING;
        public static final ForgeConfigSpec.IntValue NUDGING_LIMIT;
        public static final ForgeConfigSpec.IntValue FALLBACK_APPROACH_CELLS;
        public static final ForgeConfigSpec.IntValue AWAY_STEP_ALLOWANCE;
        public static final ForgeConfigSpec.DoubleValue RISE_RUN_RATIO;
        public static final ForgeConfigSpec.DoubleValue SOURCE_FLOW;
        public static final ForgeConfigSpec.DoubleValue FLOW_PER_CELL;
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

            DISABLE_CHUNK_SAVING = builder
                .comment(
                    "For development use only. Do not enable this setting unless directed by a RiverTale developer.",
                    "When enabled, chunk changes will not be saved to disk.")
                .define("disableChunkSaving", false);

            ENABLE_METRICS = builder
                .comment("Enable performance metrics collection. Useful for debugging but consumes memory.")
                .define("enableMetrics", DEFAULT_ENABLE_METRICS);

            ENABLE_NUDGING = builder
                .comment("Allow river paths to rotate downhill directions toward their destination. Nudged steps may go uphill.")
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

            RISE_RUN_RATIO = builder
                .comment("Minimum rise in water elevation per river cell step upriver. 0.25 raises the minimum one block every four cells; 2 raises it two blocks per cell. Zero disables the minimum grade.")
                .defineInRange("riseRunRatio", DEFAULT_RISE_RUN_RATIO, 0.0, Double.MAX_VALUE);

            SOURCE_FLOW = builder
                .comment("Starting flow at a watershed cell with no upstream links.")
                .defineInRange("sourceFlow", DEFAULT_SOURCE_FLOW, 0.0, Double.MAX_VALUE);

            FLOW_PER_CELL = builder
                .comment("Flow added once at each non-source watershed cell, after summing incoming flows.")
                .defineInRange("flowPerCell", DEFAULT_FLOW_PER_CELL, 0.0, Double.MAX_VALUE);

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
                ENABLE_METRICS.get(),
                ENABLE_NUDGING.get(),
                NUDGING_LIMIT.get(),
                FALLBACK_APPROACH_CELLS.get(),
                AWAY_STEP_ALLOWANCE.get(),
                RISE_RUN_RATIO.get(),
                SOURCE_FLOW.get(),
                FLOW_PER_CELL.get(),
                SOURCES.load(),
                CACHES.load()
            ));
        }
    }
}
