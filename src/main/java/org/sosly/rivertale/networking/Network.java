package org.sosly.rivertale.networking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.command.VisCommand;
import org.sosly.rivertale.region.Region;

public class Network {
    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(RiverTale.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals);

    private static int packetId = 0;

    public static void register() {
        CHANNEL.registerMessage(packetId++,
            Region.Packet.class,
            Region.Packet::encode,
            Region.Packet::decode,
            Region.Packet::handle);

        CHANNEL.registerMessage(packetId++,
            VisCommand.TogglePacket.class,
            VisCommand.TogglePacket::encode,
            VisCommand.TogglePacket::decode,
            VisCommand.TogglePacket::handle);
    }

    public static void sendToPlayer(Message message, ServerPlayer player) {
        CHANNEL.sendTo(message, player.connection.connection, NetworkDirection.PLAY_TO_CLIENT);
    }
}
