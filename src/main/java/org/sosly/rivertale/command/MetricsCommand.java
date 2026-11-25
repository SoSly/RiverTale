package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class MetricsCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("metrics")
            .requires(source -> source.hasPermission(2))
            .executes(context -> send(context.getSource(), MetricsReport.overview()))
            .then(Commands.literal("pipeline").executes(context -> send(context.getSource(), MetricsReport.pipeline())))
            .then(Commands.literal("rivers").executes(context -> send(context.getSource(), MetricsReport.rivers())))
            .then(Commands.literal("cache").executes(context -> send(context.getSource(), MetricsReport.cache())));
    }

    private static int send(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }
}
