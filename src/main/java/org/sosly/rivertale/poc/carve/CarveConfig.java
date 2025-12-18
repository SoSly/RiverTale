package org.sosly.rivertale.poc.carve;

import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Configuration constants for the river carving PoC.
 */
public final class CarveConfig {

    // ==================== Heightmap ====================

    /**
     * Heightmap type used for terrain queries. MOTION_BLOCKING_NO_LEAVES ignores
     * tree leaves so we get actual ground level, not canopy height.
     */
    public static final Heightmap.Types TERRAIN_HEIGHTMAP = Heightmap.Types.MOTION_BLOCKING_NO_LEAVES;

    // ==================== Elevation ====================

    /** Base Y-level for rivers at the ocean (distance = 0). */
    public static final int SEA_LEVEL = 63;

    /**
     * Y-level increase per cell of distance from ocean.
     * Water surface Y = SEA_LEVEL + (distance * ELEVATION_PER_CELL)
     */
    public static final int ELEVATION_PER_CELL = 4;

    // ==================== Channel Dimensions ====================

    /**
     * Minimum channel depth in blocks. Used for narrow rivers or steep slopes.
     */
    public static final int MIN_CHANNEL_DEPTH = 3;

    /**
     * Maximum channel depth in blocks. Achieved at MAX_WIDTH with zero slope.
     * Depth interpolates linearly between MIN and MAX based on width.
     */
    public static final int MAX_CHANNEL_DEPTH = 16;

    /**
     * Minimum river width in blocks.
     */
    public static final int MIN_WIDTH = 2;

    /**
     * Maximum river width in blocks.
     * Formula: width = MIN_WIDTH + WIDTH_PER_ACC_LOG * ln(1 + accumulation) * slopeModifier
     */
    public static final int MAX_WIDTH = 40;

    // ==================== Bank Shaping ====================

    /**
     * Controls bank slope steepness. Higher = gentler slope.
     * For every BANK_SLOPE blocks of horizontal distance, the bank rises/falls 1 block.
     * Used by both EmbankmentBuilder (slopes down) and ValleyCarver (slopes up).
     */
    public static final int BANK_SLOPE = 3;

    /**
     * How far beyond the channel to search for terrain modification (embankment/valley).
     * Total search radius = (width / 2) + MAX_SEARCH_EXTENSION
     */
    public static final int MAX_SEARCH_EXTENSION = 60;

    // ==================== Path Noise ====================

    /**
     * Amplitude of meander noise as a fraction of river width.
     * Larger values create more dramatic side-to-side wobble.
     */
    public static final double MEANDER_SCALE = 0.3;

    /**
     * Frequency of Perlin noise for meander. Lower = smoother, longer curves.
     */
    public static final double NOISE_FREQUENCY = 0.02;

    /**
     * Path position (0-1) where meander noise starts fading in from entry point.
     */
    public static final double FADE_START = 0.1;

    /**
     * Path position (0-1) where meander noise starts fading out toward exit point.
     */
    public static final double FADE_END = 0.9;

    // ==================== Path Interpolation ====================

    /**
     * Controls catenary sag as a fraction of elevation difference.
     * 0 = straight line, higher = more natural droop in the middle.
     * Elevation = linear - (elevDiff * CATENARY_RATIO * t * (1-t))
     */
    public static final double CATENARY_RATIO = 0.2;

    /**
     * Controls Bezier control point distance as a fraction of total path length.
     * Affects how far the river curves in the initial direction before bending to exit.
     */
    public static final double BEZIER_CONTROL_RATIO = 0.5;

    // ==================== Accumulation Formulas ====================

    /**
     * Width scaling per unit of log(accumulation).
     * Formula: baseWidth = MIN_WIDTH + WIDTH_PER_ACC_LOG * ln(1 + accumulation)
     * Higher values = wider rivers for the same accumulation.
     */
    public static final double WIDTH_PER_ACC_LOG = 20.0;

    /**
     * How much slope reduces width. Higher = narrower rivers on steep terrain.
     * slopeModifier = 1 / (1 + slope * SLOPE_WIDTH_FACTOR)
     */
    public static final double SLOPE_WIDTH_FACTOR = 20.0;

    /**
     * How much slope reduces depth. Higher = shallower rivers on steep terrain.
     * depthModifier = 1 / (1 + slope * SLOPE_DEPTH_FACTOR)
     */
    public static final double SLOPE_DEPTH_FACTOR = 15.0;

    private CarveConfig() {
    }
}
