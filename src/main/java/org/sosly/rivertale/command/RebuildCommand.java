package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.world.RiverShaping;

public final class RebuildCommand {
    private RebuildCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("rebuild")
            .requires(source -> source.hasPermission(2))
            .executes(RebuildCommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
        if (!CommonConfig.Spec.DISABLE_CHUNK_SAVING.get()) {
            source.sendFailure(Component.literal("River rebuild requires disableChunkSaving = true."));
            return 0;
        }
        ServerLevel level = player.serverLevel();
        RegionPos center = new RegionPos(player.blockPosition());
        if (!RiverShaping.rebuild(center, level.getChunkSource().randomState())) {
            source.sendFailure(Component.literal("River rebuild requires an active ReTerraForged overworld."));
            return 0;
        }
        VisCommand.clearOverlays(level);
        source.sendSuccess(() -> Component.literal("Queued river rebuild around region " + center + "."), false);
        return 1;
    }
}
