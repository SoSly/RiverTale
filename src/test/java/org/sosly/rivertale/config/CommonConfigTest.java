package org.sosly.rivertale.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.io.ParsingException;
import com.electronwill.nightconfig.toml.TomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    void loadsEveryNewSettingFromToml() {
        load("""
            fallbackApproachCells = 2
            awayStepAllowance = 3
            [sources]
            snowmeltMinHeight = 210
            snowmeltMaxTemperature = -0.1
            springMinGrade = 0.25
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
        assertEquals(new SourceConfig(210, -0.1, 0.25, 0.7, 0.6, 0.15, 140, 0.35, 0.25), config.sources());
        assertEquals(new CacheConfig(2000, 200, 20, 30, 40), config.caches());
    }

    @Test
    void startupCapsSamplesAtTheNumberOfChunksWithoutChangingTheRequestedCount() {
        load("cellSize = 2\nsamplesPerCell = 5");

        assertEquals(4, CommonConfig.get().samplesPerCell());
        assertEquals(5, CommonConfig.Spec.SAMPLES_PER_CELL.get().intValue());

        load("cellSize = 4\nsamplesPerCell = 5");
        assertEquals(5, CommonConfig.get().samplesPerCell());
    }

    @Test
    void loadsWholeAndFractionalRiseRunRatios() {
        load("");
        assertEquals(0.25, CommonConfig.get().riseRunRatio());

        load("riseRunRatio = 2");
        assertEquals(2.0, CommonConfig.get().riseRunRatio());

        load("riseRunRatio = 0.1");
        assertEquals(0.1, CommonConfig.get().riseRunRatio());
    }

    @Test
    void loadsFlowDefaultsAndOverridesFromToml() {
        load("");
        assertEquals(1.0, CommonConfig.get().sourceFlow());
        assertEquals(0.5, CommonConfig.get().flowPerCell());

        load("""
            sourceFlow = 2
            flowPerCell = 0.125
            """);
        assertEquals(2.0, CommonConfig.get().sourceFlow());
        assertEquals(0.125, CommonConfig.get().flowPerCell());
    }

    @Test
    void loadsCarvingSearchRadiusFromToml() {
        load("");
        assertEquals(64, CommonConfig.get().carvingSearchRadius());

        load("carvingSearchRadius = 288");
        assertEquals(288, CommonConfig.get().carvingSearchRadius());
    }

    @Test
    void disablingChunkSavingIsOptIn() {
        load("");
        assertFalse(CommonConfig.get().disableChunkSaving());

        load("disableChunkSaving = true");
        assertTrue(CommonConfig.get().disableChunkSaving());

        CommonConfig.Spec.SPEC.setConfig(new TomlParser().parse("disableChunkSaving = false"));
        assertTrue(CommonConfig.get().disableChunkSaving());
    }

    @Test
    void readsFreshTuningWithoutApplyingRestartOnlyEdits(@TempDir Path directory) throws IOException {
        load("disableChunkSaving = true");
        CommonConfig active = CommonConfig.get();
        Path file = directory.resolve(CommonConfig.Spec.FILE_NAME);
        Files.writeString(file, """
            regionSize = 32
            cellSize = 1
            samplesPerCell = 8
            disableChunkSaving = false
            enableMetrics = true
            enableNudging = false
            nudgingLimit = 12
            fallbackApproachCells = 0
            awayStepAllowance = 2
            riseRunRatio = 0.5
            sourceFlow = 2
            flowPerCell = 0.75
            carvingSearchRadius = 0
            [sources]
            springMinGrade = 0.25
            [caches]
            cells = 12
            """);

        CommonConfig tuning = CommonConfig.Spec.readTuning(file);

        assertEquals(active.regionSize(), tuning.regionSize());
        assertEquals(active.cellSize(), tuning.cellSize());
        assertSame(active.caches(), tuning.caches());
        assertTrue(tuning.disableChunkSaving());
        assertEquals(8, tuning.samplesPerCell());
        assertTrue(tuning.enableMetrics());
        assertFalse(tuning.enableNudging());
        assertEquals(12, tuning.nudgingLimit());
        assertEquals(0, tuning.fallbackApproachCells());
        assertEquals(2, tuning.awayStepAllowance());
        assertEquals(0.5, tuning.riseRunRatio());
        assertEquals(2, tuning.sourceFlow());
        assertEquals(0.75, tuning.flowPerCell());
        assertEquals(0, tuning.carvingSearchRadius());
        assertEquals(0.25, tuning.sources().springMinGrade());
        assertSame(active, CommonConfig.get());

        Files.writeString(file, "nudgingLimit = 0");
        assertEquals(0, CommonConfig.Spec.readTuning(file).nudgingLimit());
    }

    @Test
    void rebuildCapsSamplesAgainstTheActiveGridWithoutRewritingToml(@TempDir Path directory) throws IOException {
        load("cellSize = 2\nsamplesPerCell = 4");
        Path file = directory.resolve(CommonConfig.Spec.FILE_NAME);
        String toml = "cellSize = 8\nsamplesPerCell = 5";
        Files.writeString(file, toml);

        CommonConfig tuning = CommonConfig.Spec.readTuning(file);

        assertEquals(2, tuning.cellSize());
        assertEquals(4, tuning.samplesPerCell());
        assertEquals(toml, Files.readString(file));
    }

    @Test
    void rejectsInvalidTuning(@TempDir Path directory) throws IOException {
        load("");
        CommonConfig active = CommonConfig.get();
        Path file = directory.resolve(CommonConfig.Spec.FILE_NAME);
        assertThrows(IOException.class, () -> CommonConfig.Spec.readTuning(file));

        Files.writeString(file, "nudgingLimit = [");
        assertThrows(ParsingException.class, () -> CommonConfig.Spec.readTuning(file));

        Files.writeString(file, "samplesPerCell = 65");
        assertThrows(IllegalArgumentException.class, () -> CommonConfig.Spec.readTuning(file));

        Files.writeString(file, "nudgingLimit = 0.5");
        assertThrows(IllegalArgumentException.class, () -> CommonConfig.Spec.readTuning(file));

        Files.writeString(file, "[sources]\ncraterChance = 2.0");
        assertThrows(IllegalArgumentException.class, () -> CommonConfig.Spec.readTuning(file));
        assertSame(active, CommonConfig.get());
    }

    @Test
    void invalidTomlValuesAreCorrectedBeforeLoading() {
        load("""
            awayStepAllowance = -1
            fallbackApproachCells = -1
            riseRunRatio = -0.25
            sourceFlow = -1
            flowPerCell = -0.5
            [sources]
            craterChance = 2.0
            [caches]
            samples = 0
            """);
        assertEquals(0, CommonConfig.get().awayStepAllowance());
        assertEquals(0, CommonConfig.get().fallbackApproachCells());
        assertEquals(0.0, CommonConfig.get().riseRunRatio());
        assertEquals(0.0, CommonConfig.get().sourceFlow());
        assertEquals(0.0, CommonConfig.get().flowPerCell());
        assertEquals(1.0, CommonConfig.get().sources().craterChance());
        assertEquals(1, CommonConfig.get().caches().samples());
    }

    @Test
    void directConfigurationRejectsInvalidLimitsAndProbabilities() {
        assertThrows(IllegalArgumentException.class, () -> new CacheConfig(0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig(175, Double.NaN, 0.125, 0.5, 0.4, 0.3, 100, 0.2, 0.05));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig(175, 0.2, -0.125, 0.5, 0.4, 0.3, 100, 0.2, 0.05));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig(175, 0.2, Double.NaN, 0.5, 0.4, 0.3, 100, 0.2, 0.05));
        assertThrows(IllegalArgumentException.class, () -> new SourceConfig(175, 0.2, 0.125, 0.5, 0.4, 0.3, 100, 0.2, 1.1));
        assertThrows(IllegalArgumentException.class, () -> new CommonConfig(8, 1, 1, false, false, 16,
            5, Integer.MAX_VALUE, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> new CommonConfig(8, 1, 1, false, false, 16,
            5, 10, Double.POSITIVE_INFINITY, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> new CommonConfig(8, 1, 1, false, false, 16,
            5, 10, 0.25, Double.NaN, 0.5, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        assertThrows(IllegalArgumentException.class, () -> new CommonConfig(8, 1, 1, false, false, 16,
            5, 10, 0.25, 1.0, Double.POSITIVE_INFINITY, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
    }

    private void load(String toml) {
        CommentedConfig data = new TomlParser().parse(toml);
        CommonConfig.Spec.SPEC.setConfig(data);
        CommonConfig.Spec.load();
    }
}
