package org.sosly.rivertale.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.world.RiverShaping;

@Mod.EventBusSubscriber
public class RiverTaleCommand {

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rivertale")
            .executes(context -> {
                context.getSource().sendSuccess(() -> Component.literal(
                    "Rivertale terrain tools require an RTF overworld. Create an RTF world with River Count set to 0."
                ), false);
                return 1;
            })
            .then(BoundariesCommand.register().requires(RiverTaleCommand::supportsTerrain))
            .then(DebugCommand.register().requires(RiverTaleCommand::supportsTerrain))
            .then(MetricsCommand.register())
            .then(RebuildCommand.register())
            .then(RegionSetCommand.register().requires(RiverTaleCommand::supportsTerrain))
            .then(SampleCommand.register().requires(RiverTaleCommand::supportsTerrain))
            .then(VisCommand.register().requires(RiverTaleCommand::supportsTerrain))
        );
    }

    private static boolean supportsTerrain(CommandSourceStack source) {
        return RiverShaping.isActive(source.getLevel().getChunkSource().randomState());
    }
}
