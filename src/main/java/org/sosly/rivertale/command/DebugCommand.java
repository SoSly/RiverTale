package org.sosly.rivertale.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.sosly.rivertale.client.debug.DebugVisualizationManager;

public class DebugCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("rivertale")
                .then(Commands.literal("debug")
                    .then(Commands.literal("show-cells")
                        .requires(source -> source.hasPermission(2))
                        .executes(DebugCommand::showCells)
                    )
                )
        );
    }

    private static int showCells(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ServerPlayer player = source.getPlayerOrException();
            boolean enabled = DebugVisualizationManager.toggle(player);

            if (enabled) {
                source.sendSuccess(
                    () -> Component.literal("Debug visualization enabled"),
                    false
                );
            } else {
                source.sendSuccess(
                    () -> Component.literal("Debug visualization disabled"),
                    false
                );
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Command must be run by a player"));
            return 0;
        }
    }
}
