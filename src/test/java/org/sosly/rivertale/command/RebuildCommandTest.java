package org.sosly.rivertale.command;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.world.RiverShaping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RebuildCommandTest {
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();

    @BeforeEach
    void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        dispatcher.register(RebuildCommand.register());
    }

    @AfterEach
    void tearDown() {
        CommonConfig.Spec.SPEC.setConfig((CommentedConfig) null);
    }

    @Test
    void rejectsNonOperatorsEvenWhenSavingIsDisabled() {
        setChunkSavingDisabled(true);
        CommandSourceStack source = mock(CommandSourceStack.class);
        try (MockedStatic<RiverShaping> shaping = mockStatic(RiverShaping.class)) {
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("rebuild", source));
            shaping.verifyNoInteractions();
        }
    }

    @Test
    void rejectsOperatorsWhenSavingIsEnabledWithoutTouchingRiverOrOverlayData() throws Exception {
        setChunkSavingDisabled(false);
        ServerPlayer player = mock(ServerPlayer.class);
        CommandSourceStack source = operator(player);
        try (MockedStatic<RiverShaping> shaping = mockStatic(RiverShaping.class)) {
            assertEquals(0, dispatcher.execute("rebuild", source));
            ArgumentCaptor<Component> message = ArgumentCaptor.forClass(Component.class);
            verify(source).sendFailure(message.capture());
            assertEquals("River rebuild requires disableChunkSaving = true.", message.getValue().getString());
            shaping.verifyNoInteractions();
            verify(player, never()).serverLevel();
        }
    }

    @Test
    void rebuildUsesThePlayersRegionAndClearsOverlaysInTheirWorld() throws Exception {
        setChunkSavingDisabled(true);
        ServerPlayer player = mock(ServerPlayer.class);
        ServerLevel level = mock(ServerLevel.class, RETURNS_DEEP_STUBS);
        RandomState state = mock(RandomState.class);
        BlockPos position = new BlockPos(-1234, 100, 5678);
        when(player.blockPosition()).thenReturn(position);
        when(player.serverLevel()).thenReturn(level);
        when(level.getChunkSource().randomState()).thenReturn(state);
        CommandSourceStack source = operator(player);
        RegionPos center = new RegionPos(position);
        when(level.players()).thenReturn(List.of());
        try (MockedStatic<RiverShaping> shaping = mockStatic(RiverShaping.class)) {
            assertEquals(0, dispatcher.execute("rebuild", source));
            verify(level, never()).players();

            shaping.when(() -> RiverShaping.rebuild(center, state)).thenReturn(true);
            assertEquals(1, dispatcher.execute("rebuild", source));
            verify(level).players();
        }
    }

    private static CommandSourceStack operator(ServerPlayer player) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.hasPermission(2)).thenReturn(true);
        when(source.getPlayer()).thenReturn(player);
        return source;
    }

    private static void setChunkSavingDisabled(boolean disabled) {
        CommonConfig.Spec.SPEC.setConfig(new TomlParser().parse("disableChunkSaving = " + disabled));
    }
}
