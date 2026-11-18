package org.sosly.rivertale.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sosly.rivertale.capability.FlowDirectionCapability;
import org.sosly.rivertale.capability.FlowDirectionData;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.physics.FluidFlow;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class FlowUnlockConfigTest {
    private CommonConfig original;

    @BeforeEach
    void setUp() {
        original = CommonConfig.get();
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @AfterEach
    void tearDown() {
        CommonConfig.Spec.SPEC.setConfig((CommentedConfig) null);
        CommonConfig.set(original);
    }

    @Test
    void timeoutCanDisableOrPermitTheNextSpreadAfterAnEdit() {
        LevelAccessor level = mock(LevelAccessor.class);
        LevelChunk chunk = mock(LevelChunk.class);
        BlockState water = mock(BlockState.class);
        FluidState fluid = mock(FluidState.class);
        FlowDirectionData directions = mock(FlowDirectionData.class);
        when(level.getChunk(any(BlockPos.class))).thenReturn(chunk);
        when(level.getBlockState(any(BlockPos.class))).thenReturn(water);
        when(water.is(Blocks.WATER)).thenReturn(true);
        when(directions.get(anyInt(), anyInt())).thenReturn(FlowDirection.SOUTH);
        BlockPos target = new BlockPos(1, 64, 1);
        try (MockedStatic<CapabilityManager> manager = mockStatic(CapabilityManager.class)) {
            manager.when(() -> CapabilityManager.get(any(CapabilityToken.class))).thenReturn(mock(Capability.class));
            try (MockedStatic<FlowDirectionCapability> capabilities = mockStatic(FlowDirectionCapability.class)) {
                capabilities.when(() -> FlowDirectionCapability.get(chunk)).thenReturn(directions);
                configure(0);
                FluidFlow.unlock(target);
                assertFalse(FluidFlow.allowSpread(level, target, water, Direction.SOUTH, fluid));
                configure(60_000);
                FluidFlow.unlock(target);
                assertTrue(FluidFlow.allowSpread(level, target, water, Direction.SOUTH, fluid));
                assertFalse(FluidFlow.allowSpread(level, target, water, Direction.SOUTH, fluid));
            }
        }
    }

    private void configure(int millis) {
        CommentedConfig data = new TomlParser().parse("flowUnlockMillis = " + millis);
        CommonConfig.Spec.SPEC.setConfig(data);
        CommonConfig.Spec.load();
    }
}
