package org.sosly.rivertale.command;

import com.electronwill.nightconfig.core.io.ParsingException;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.loading.FMLPaths;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.world.RiverShaping;

public final class RebuildCommand {
    private RebuildCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return register(FMLPaths.CONFIGDIR.get().resolve(CommonConfig.Spec.FILE_NAME));
    }

    static LiteralArgumentBuilder<CommandSourceStack> register(Path configFile) {
        return Commands.literal("rebuild")
            .requires(source -> source.hasPermission(2))
            .executes(context -> execute(context, configFile));
    }

    private static int execute(CommandContext<CommandSourceStack> context, Path configFile) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }
        if (!CommonConfig.get().disableChunkSaving()) {
            source.sendFailure(Component.literal("River rebuild requires disableChunkSaving = true."));
            return 0;
        }
        CommonConfig tuning;
        try {
            tuning = CommonConfig.Spec.readTuning(configFile);
        } catch (IOException | ParsingException | IllegalArgumentException failure) {
            source.sendFailure(Component.literal("Could not reload RiverTale tuning: " + failure.getMessage()));
            return 0;
        }

        ServerLevel level = player.serverLevel();
        RegionPos center = new RegionPos(player.blockPosition());
        if (!RiverShaping.rebuild(center, level.getChunkSource().randomState(), tuning)) {
            source.sendFailure(Component.literal("River rebuild requires an active ReTerraForged overworld."));
            return 0;
        }
        VisCommand.clearOverlays(level);
        source.sendSuccess(() -> Component.literal("Queued river rebuild with reloaded tuning around region " + center + "."), false);
        return 1;
    }
}
