package org.sosly.rivertale.config;

import net.minecraftforge.common.ForgeConfigSpec;

public record SourceConfig(
    int snowmeltMinHeight,
    double snowmeltMaxTemperature,
    double springMinGrade,
    double springMaxRidges,
    double seepMinVegetation,
    double seepMaxErosion,
    int craterMinHeight,
    double craterMinTemperature,
    double craterChance
) {
    public static final SourceConfig DEFAULT = new SourceConfig(175, 0.2, 0.125, 0.5, 0.4, 0.3, 100, 0.2, 0.05);

    public SourceConfig {
        if (!Double.isFinite(snowmeltMaxTemperature)) {
            throw new IllegalArgumentException("snowmeltMaxTemperature must be finite");
        }
        if (!Double.isFinite(springMinGrade) || springMinGrade < 0) {
            throw new IllegalArgumentException("springMinGrade must be finite and nonnegative");
        }
        if (!Double.isFinite(springMaxRidges)) {
            throw new IllegalArgumentException("springMaxRidges must be finite");
        }
        if (!Double.isFinite(seepMinVegetation)) {
            throw new IllegalArgumentException("seepMinVegetation must be finite");
        }
        if (!Double.isFinite(seepMaxErosion)) {
            throw new IllegalArgumentException("seepMaxErosion must be finite");
        }
        if (!Double.isFinite(craterMinTemperature)) {
            throw new IllegalArgumentException("craterMinTemperature must be finite");
        }
        if (!Double.isFinite(craterChance)) {
            throw new IllegalArgumentException("craterChance must be finite");
        }
        if (craterChance < 0 || craterChance > 1) {
            throw new IllegalArgumentException("craterChance must be between 0 and 1");
        }
    }

    static final class Spec {
        private final ForgeConfigSpec.IntValue snowmeltMinHeight;
        private final ForgeConfigSpec.DoubleValue snowmeltMaxTemperature;
        private final ForgeConfigSpec.DoubleValue springMinGrade;
        private final ForgeConfigSpec.DoubleValue springMaxRidges;
        private final ForgeConfigSpec.DoubleValue seepMinVegetation;
        private final ForgeConfigSpec.DoubleValue seepMaxErosion;
        private final ForgeConfigSpec.IntValue craterMinHeight;
        private final ForgeConfigSpec.DoubleValue craterMinTemperature;
        private final ForgeConfigSpec.DoubleValue craterChance;

        Spec(ForgeConfigSpec.Builder builder) {
            snowmeltMinHeight = builder.comment("Minimum absolute Y for snowmelt sources (inclusive).")
                .defineInRange("snowmeltMinHeight", DEFAULT.snowmeltMinHeight(), Integer.MIN_VALUE, Integer.MAX_VALUE);
            snowmeltMaxTemperature = builder.comment("Snowmelt sources require sampled temperature below this value (exclusive).")
                .defineInRange("snowmeltMaxTemperature", DEFAULT.snowmeltMaxTemperature(), -Double.MAX_VALUE, Double.MAX_VALUE);
            springMinGrade = builder.comment("Minimum downhill grade from a spring to a land neighbor (vertical drop / horizontal distance, inclusive).")
                .defineInRange("springMinGrade", DEFAULT.springMinGrade(), 0.0, Double.MAX_VALUE);
            springMaxRidges = builder.comment("Spring sources require sampled ridges below this value (exclusive).")
                .defineInRange("springMaxRidges", DEFAULT.springMaxRidges(), -Double.MAX_VALUE, Double.MAX_VALUE);
            seepMinVegetation = builder.comment("Seep sources require sampled vegetation/moisture above this value (exclusive).")
                .defineInRange("seepMinVegetation", DEFAULT.seepMinVegetation(), -Double.MAX_VALUE, Double.MAX_VALUE);
            seepMaxErosion = builder.comment("Seep sources require sampled erosion below this value (exclusive).")
                .defineInRange("seepMaxErosion", DEFAULT.seepMaxErosion(), -Double.MAX_VALUE, Double.MAX_VALUE);
            craterMinHeight = builder.comment("Minimum absolute Y for crater sources (inclusive).")
                .defineInRange("craterMinHeight", DEFAULT.craterMinHeight(), Integer.MIN_VALUE, Integer.MAX_VALUE);
            craterMinTemperature = builder.comment("Minimum sampled temperature for crater sources (inclusive).")
                .defineInRange("craterMinTemperature", DEFAULT.craterMinTemperature(), -Double.MAX_VALUE, Double.MAX_VALUE);
            craterChance = builder.comment("Chance that an eligible cell becomes a crater source, from 0 to 1. Uses the existing seeded random value.")
                .defineInRange("craterChance", DEFAULT.craterChance(), 0.0, 1.0);
        }

        SourceConfig load() {
            return new SourceConfig(
                snowmeltMinHeight.get(),
                snowmeltMaxTemperature.get(),
                springMinGrade.get(),
                springMaxRidges.get(),
                seepMinVegetation.get(),
                seepMaxErosion.get(),
                craterMinHeight.get(),
                craterMinTemperature.get(),
                craterChance.get()
            );
        }

        SourceConfig load(TuningConfigValues values) {
            return new SourceConfig(
                values.get(snowmeltMinHeight),
                values.get(snowmeltMaxTemperature),
                values.get(springMinGrade),
                values.get(springMaxRidges),
                values.get(seepMinVegetation),
                values.get(seepMaxErosion),
                values.get(craterMinHeight),
                values.get(craterMinTemperature),
                values.get(craterChance)
            );
        }
    }
}
