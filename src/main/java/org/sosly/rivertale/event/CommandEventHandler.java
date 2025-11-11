package org.sosly.rivertale.event;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.command.DebugCommand;

@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CommandEventHandler {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        DebugCommand.register(dispatcher);
    }
}
