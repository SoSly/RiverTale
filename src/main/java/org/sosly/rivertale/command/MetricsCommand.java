package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.sosly.rivertale.metric.Distribution;
import org.sosly.rivertale.metric.Ratio;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public class MetricsCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("metrics")
            .requires(source -> source.hasPermission(2))
            .executes(MetricsCommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        Map<String, Timer> timers = Store.timers();
        Map<String, Ratio> ratios = Store.ratios();
        Map<String, Distribution> distributions = Store.distributions();

        if (timers.isEmpty() && ratios.isEmpty() && distributions.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No metrics recorded yet."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal("=== RiverTale Metrics ==="), false);

        for (Map.Entry<String, Timer> entry : timers.entrySet()) {
            String name = simplifyName(entry.getKey());
            Timer.Snapshot snapshot = entry.getValue().snapshot();
            source.sendSuccess(() -> formatTimer(name, snapshot), false);
        }

        for (Map.Entry<String, Distribution> entry : distributions.entrySet()) {
            String name = simplifyName(entry.getKey());
            Distribution.Snapshot snapshot = entry.getValue().snapshot();
            source.sendSuccess(() -> formatDistribution(name, snapshot), false);
        }

        for (Map.Entry<String, Ratio> entry : ratios.entrySet()) {
            String name = simplifyName(entry.getKey());
            Ratio ratio = entry.getValue();
            source.sendSuccess(() -> formatRatio(name, ratio), false);
        }

        return 1;
    }

    private static String simplifyName(String fullName) {
        int lastDot = fullName.lastIndexOf('.');
        if (lastDot >= 0) {
            return fullName.substring(lastDot + 1);
        }
        return fullName;
    }

    private static Component formatTimer(String name, Timer.Snapshot snapshot) {
        String msg = String.format("%s: %d calls | avg=%.1fus min=%.1fus max=%.1fus med=%.1fus",
            name, snapshot.count(), snapshot.avg(), snapshot.min(), snapshot.max(), snapshot.median());
        return Component.literal(msg);
    }

    private static Component formatDistribution(String name, Distribution.Snapshot snapshot) {
        String msg = String.format("%s: %d samples | avg=%.1f min=%.1f max=%.1f med=%.1f",
            name, snapshot.count(), snapshot.avg(), snapshot.min(), snapshot.max(), snapshot.median());
        return Component.literal(msg);
    }

    private static Component formatRatio(String name, Ratio ratio) {
        String msg = String.format("%s: %d/%d (%.1f%%)",
            name, ratio.successes(), ratio.attempts(), ratio.rate() * 100);
        return Component.literal(msg);
    }
}
