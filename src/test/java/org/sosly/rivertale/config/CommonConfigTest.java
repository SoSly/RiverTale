package org.sosly.rivertale.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommonConfigTest {
    private CommonConfig original;

    @BeforeEach
    void setUp() {
        original = CommonConfig.get();
    }

    @AfterEach
    void tearDown() {
        CommonConfig.Spec.SPEC.setConfig((CommentedConfig) null);
        CommonConfig.set(original);
    }

    @Test
    void partialTomlRetainsCustomValuesAndUsesTuningDefaults() {
        load("""
            nudgingLimit = 12
            """);
        CommonConfig config = CommonConfig.get();
        assertEquals(24, config.regionSize());
        assertEquals(4, config.cellSize());
        assertEquals(5, config.samplesPerCell());
        assertFalse(config.enableMetrics());
        assertEquals(12, config.nudgingLimit());
        assertEquals(5, config.fallbackApproachCells());
        assertEquals(1, config.awayStepAllowance());
        assertEquals(5000, config.flowUnlockMillis());
        assertFalse(config.localRiverPreview());
        assertEquals(new SourceConfig(175, 0.2, 113, 0.5, 0.4, 0.3, 100, 0.2, 0.05), config.sources());
        assertEquals(new CacheConfig(1_000_000, 50_000, 100, 100, 100), config.caches());
    }

    @Test
    void loadsEveryNewSettingFromToml() {
        load("""
            fallbackApproachCells = 2
            awayStepAllowance = 3
            flowUnlockMillis = 750
            localRiverPreview = true
            [sources]
            snowmeltMinHeight = 210
            snowmeltMaxTemperature = -0.1
            springMinHeight = 130
            springMaxRidges = 0.7
            seepMinVegetation = 0.6
            seepMaxErosion = 0.15
            craterMinHeight = 140
            craterMinTemperature = 0.35
            craterChance = 0.25
            [caches]
            samples = 2000
            cells = 200
            regions = 20
            regionTypes = 30
            clientRegions = 40
            """);
        CommonConfig config = CommonConfig.get();
        assertEquals(2, config.fallbackApproachCells());
        assertEquals(3, config.awayStepAllowance());
        assertEquals(750, config.flowUnlockMillis());
        assertTrue(config.localRiverPreview());
        assertEquals(new SourceConfig(210, -0.1, 130, 0.7, 0.6, 0.15, 140, 0.35, 0.25), config.sources());
        assertEquals(new CacheConfig(2000, 200, 20, 30, 40), config.caches());
    }

    @Test
    void invalidTomlValuesAreCorrectedBeforeLoading() {
        load("""
            awayStepAllowance = -1
            fallbackApproachCells = -1
            flowUnlockMillis = -1
            [sources]
            craterChance = 2.0
            [caches]
            samples = 0
            """);
        assertEquals(0, CommonConfig.get().awayStepAllowance());
        assertEquals(0, CommonConfig.get().fallbackApproachCells());
        assertEquals(0, CommonConfig.get().flowUnlockMillis());
        assertEquals(1.0, CommonConfig.get().sources().craterChance());
        assertEquals(1, CommonConfig.get().caches().samples());
    }

    @Test
    void directConfigurationRejectsInvalidLimitsAndProbabilities() {
        assertThrows(IllegalArgumentException.class, () -> new CacheConfig(0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig(175, Double.NaN, 113, 0.5, 0.4, 0.3, 100, 0.2, 0.05));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig(175, 0.2, 113, 0.5, 0.4, 0.3, 100, 0.2, 1.1));
        assertThrows(IllegalArgumentException.class, () -> new CommonConfig(8, 1, 1, 0, 1, 3, 288, false, false, 16,
            5, Integer.MAX_VALUE, SourceConfig.DEFAULT, CacheConfig.DEFAULT, 5000, false));
    }

    private void load(String toml) {
        CommentedConfig data = new TomlParser().parse(toml);
        CommonConfig.Spec.SPEC.setConfig(data);
        CommonConfig.Spec.load();
    }
}
