package org.sosly.rivertale.poc.carve;

public final class CarveConfig {
    public static final int SEA_LEVEL = 63;
    public static final int ELEVATION_PER_CELL = 4;
    public static final int MIN_CHANNEL_DEPTH = 3;
    public static final int MAX_CHANNEL_DEPTH = 8;
    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 40;
    public static final int VALLEY_SLOPE = 30;
    public static final int EMBANKMENT_SLOPE = 40;
    public static final int MAX_SEARCH_EXTENSION = 60;
    public static final double MEANDER_SCALE = 0.3;
    public static final double NOISE_FREQUENCY = 0.02;
    public static final double FADE_START = 0.1;
    public static final double FADE_END = 0.9;

    private CarveConfig() {
    }
}
