package org.sosly.rivertale.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.sosly.rivertale.client.DebugRenderer;

public class RiverTaleDebugCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("rivertale")
                .then(Commands.literal("debug")
                    .executes(RiverTaleDebugCommand::toggleDebug)
                )
        );
    }

    private static int toggleDebug(CommandContext<CommandSourceStack> context) {
        if (!context.getSource().isPlayer()) {
            context.getSource().sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        boolean newState = DebugRenderer.toggleDebugMode();
        if (newState) {
            context.getSource().sendSuccess(() -> Component.literal("River debug visualization enabled"), false);
            context.getSource().sendSuccess(() -> Component.literal("Red beams = high river potential (ridge)"), false);
            context.getSource().sendSuccess(() -> Component.literal("Blue beams = low river potential (valley)"), false);
        } else {
            context.getSource().sendSuccess(() -> Component.literal("River debug visualization disabled"), false);
        }
        return 1;
    }
}
