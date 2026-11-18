package org.sosly.rivertale.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Crater;
import org.sosly.rivertale.cell.feature.Seep;
import org.sosly.rivertale.cell.feature.Snowmelt;
import org.sosly.rivertale.cell.feature.Spring;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.world.RiverShaping;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SourceConfigTest {
    private CommonConfig original;
    private CommentedConfig data;
    private Cell cell;

    @BeforeEach
    void setUp() {
        original = CommonConfig.get();
        data = new TomlParser().parse("""
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
            """);
        load();
        cell = mock(Cell.class);
    }

    @AfterEach
    void tearDown() {
        CommonConfig.Spec.SPEC.setConfig((CommentedConfig) null);
        CommonConfig.set(original);
    }

    @Test
    void snowmeltUsesConfiguredHeightAndTemperatureBoundaries() {
        Snowmelt handler = new Snowmelt();
        when(cell.averageEstimatedTerrainHeight()).thenReturn(210);
        when(cell.averageTemperature()).thenReturn(-0.11);
        assertTrue(handler.classify(cell, null));
        when(cell.averageEstimatedTerrainHeight()).thenReturn(209);
        assertFalse(handler.classify(cell, null));
        when(cell.averageEstimatedTerrainHeight()).thenReturn(210);
        when(cell.averageTemperature()).thenReturn(-0.1);
        assertFalse(handler.classify(cell, null));
        when(cell.averageTemperature()).thenReturn(-0.11);
        when(cell.hasInflowingNeighbor()).thenReturn(true);
        assertFalse(handler.classify(cell, null));
    }

    @Test
    void springsUseConfiguredHeightAndRidgesBoundaries() {
        Spring handler = new Spring();
        when(cell.averageEstimatedTerrainHeight()).thenReturn(131);
        when(cell.averageRidges()).thenReturn(0.6);
        assertTrue(handler.classify(cell, null));
        when(cell.averageEstimatedTerrainHeight()).thenReturn(130);
        assertFalse(handler.classify(cell, null));
        when(cell.averageEstimatedTerrainHeight()).thenReturn(131);
        when(cell.averageRidges()).thenReturn(0.7);
        assertFalse(handler.classify(cell, null));
    }

    @Test
    void seepsUseConfiguredMoistureAndErosionBoundaries() {
        Seep handler = new Seep();
        when(cell.averageVegetation()).thenReturn(0.61);
        when(cell.averageErosion()).thenReturn(0.14);
        assertTrue(handler.classify(cell, null));
        when(cell.averageVegetation()).thenReturn(0.6);
        assertFalse(handler.classify(cell, null));
        when(cell.averageVegetation()).thenReturn(0.61);
        when(cell.averageErosion()).thenReturn(0.15);
        assertFalse(handler.classify(cell, null));
    }

    @Test
    void cratersUseConfiguredEligibilityAndProbabilityAgainstTheSameRandomDraw() {
        Crater handler = new Crater();
        RandomState state = mock(RandomState.class);
        PositionalRandomFactory factory = mock(PositionalRandomFactory.class);
        RandomSource random = mock(RandomSource.class);
        when(state.getOrCreateRandomFactory(any())).thenReturn(factory);
        when(factory.at(anyInt(), anyInt(), anyInt())).thenReturn(random);
        when(random.nextFloat()).thenReturn(0.24f);
        when(cell.pos()).thenReturn(new CellPos(3, 3));
        when(cell.averageEstimatedTerrainHeight()).thenReturn(140);
        when(cell.averageTemperature()).thenReturn(0.35);
        try (MockedStatic<RiverShaping> shaping = mockStatic(RiverShaping.class)) {
            shaping.when(RiverShaping::getRandomState).thenReturn(state);
            assertTrue(handler.classify(cell, null));
            when(cell.averageEstimatedTerrainHeight()).thenReturn(139);
            assertFalse(handler.classify(cell, null));
            when(cell.averageEstimatedTerrainHeight()).thenReturn(140);
            when(cell.averageTemperature()).thenReturn(0.34);
            assertFalse(handler.classify(cell, null));
            when(cell.averageTemperature()).thenReturn(0.35);
            when(random.nextFloat()).thenReturn(0.25f);
            assertFalse(handler.classify(cell, null));
            data.set("sources.craterChance", 0.26);
            load();
            assertTrue(handler.classify(cell, null));
            data.set("sources.craterChance", 0.0);
            load();
            assertFalse(handler.classify(cell, null));
            data.set("sources.craterChance", 1.0);
            load();
            assertTrue(handler.classify(cell, null));
        }
    }

    private void load() {
        CommonConfig.Spec.SPEC.setConfig(data);
        CommonConfig.Spec.load();
    }
}
