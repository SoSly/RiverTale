package org.sosly.rivertale.config;

import net.minecraftforge.common.ForgeConfigSpec;

public record CacheConfig(
    int samples,
    int cells,
    int regions,
    int regionTypes,
    int clientRegions
) {
    public static final CacheConfig DEFAULT = new CacheConfig(1_000_000, 50_000, 100, 100, 100);

    public CacheConfig {
        if (samples < 1 || cells < 1 || regions < 1 || regionTypes < 1 || clientRegions < 1) {
            throw new IllegalArgumentException("Cache capacities must be positive");
        }
    }

    static final class Spec {
        private final ForgeConfigSpec.IntValue samples;
        private final ForgeConfigSpec.IntValue cells;
        private final ForgeConfigSpec.IntValue regions;
        private final ForgeConfigSpec.IntValue regionTypes;
        private final ForgeConfigSpec.IntValue clientRegions;

        Spec(ForgeConfigSpec.Builder builder) {
            samples = builder.comment("Maximum cached terrain samples; each entry represents one sampled chunk.")
                .defineInRange("samples", DEFAULT.samples(), 1, Integer.MAX_VALUE);
            cells = builder.comment("Maximum cached cells.")
                .defineInRange("cells", DEFAULT.cells(), 1, Integer.MAX_VALUE);
            regions = builder.comment("Maximum cached regions. Eviction also removes affected cached watersheds.")
                .defineInRange("regions", DEFAULT.regions(), 1, Integer.MAX_VALUE);
            regionTypes = builder.comment("Maximum cached region classifications.")
                .defineInRange("regionTypes", DEFAULT.regionTypes(), 1, Integer.MAX_VALUE);
            clientRegions = builder.comment("Maximum cached region visualization packets on the client.")
                .defineInRange("clientRegions", DEFAULT.clientRegions(), 1, Integer.MAX_VALUE);
        }

        CacheConfig load() {
            return new CacheConfig(
                samples.get(),
                cells.get(),
                regions.get(),
                regionTypes.get(),
                clientRegions.get()
            );
        }
    }
}
