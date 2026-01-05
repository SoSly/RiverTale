package org.sosly.rivertale.command;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.poc.carve.CarveCommand;
import org.sosly.rivertale.poc.fill.FillCommand;

@Mod.EventBusSubscriber
public class RiverTaleCommand {

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rivertale")
            .then(DebugCommand.register())
            .then(MetricsCommand.register())
            .then(SampleCommand.register())
            .then(VisCommand.register())
            .then(Commands.literal("poc")
                .then(CarveCommand.register())
                .then(FillCommand.register())
            )
        );
    }
}
