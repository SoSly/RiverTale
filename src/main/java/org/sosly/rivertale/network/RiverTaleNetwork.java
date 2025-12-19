package org.sosly.rivertale.network;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import org.sosly.rivertale.RiverTale;

public class RiverTaleNetwork {

    private static final String PROTOCOL_VERSION = "1";
    private static SimpleChannel channel;
    private static int packetId = 0;

    public static void register() {
        channel = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(RiverTale.MOD_ID, "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
        );

        channel.registerMessage(
            packetId++,
            VisualizeCellsPacket.class,
            VisualizeCellsPacket::encode,
            VisualizeCellsPacket::decode,
            VisualizeCellsPacket::handle
        );
    }

    public static void sendToPlayer(Object packet, ServerPlayer player) {
        channel.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }
}
