package org.sosly.rivertale.world;

public record WorldSettings(int seaLevel) {
    private static WorldSettings instance;

    public static void init(int seaLevel) {
        instance = new WorldSettings(seaLevel);
    }

    public static void shutdown() {
        instance = null;
    }

    public static WorldSettings get() {
        if (instance == null) {
            throw new IllegalStateException("WorldSettings not initialized");
        }
        return instance;
    }
}
