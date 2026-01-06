package org.sosly.rivertale.networking;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.rivertale.client.FlowDirectionClientCache;
import org.sosly.rivertale.core.FlowDirection;

public class FlowDirectionSyncPacket extends Message {
    private final ChunkPos chunkPos;
    private final byte[] data;

    public FlowDirectionSyncPacket(ChunkPos chunkPos, FlowDirection[][] directions) {
        this.chunkPos = chunkPos;
        this.data = encode(directions);
    }

    private FlowDirectionSyncPacket(ChunkPos chunkPos, byte[] data) {
        this.chunkPos = chunkPos;
        this.data = data;
    }

    private static byte[] encode(FlowDirection[][] directions) {
        byte[] result = new byte[256];
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                FlowDirection dir = directions[x][z];
                result[x * 16 + z] = dir == null ? -1 : (byte) dir.ordinal();
            }
        }
        return result;
    }

    private static FlowDirection[][] decode(byte[] data) {
        FlowDirection[][] result = new FlowDirection[16][16];
        FlowDirection[] values = FlowDirection.values();
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                byte b = data[x * 16 + z];
                result[x][z] = (b < 0 || b >= values.length) ? null : values[b];
            }
        }
        return result;
    }

    public static void encode(FlowDirectionSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeInt(msg.chunkPos.x);
        buf.writeInt(msg.chunkPos.z);
        buf.writeByteArray(msg.data);
    }

    public static FlowDirectionSyncPacket decode(FriendlyByteBuf buf) {
        int x = buf.readInt();
        int z = buf.readInt();
        byte[] data = buf.readByteArray(256);
        return new FlowDirectionSyncPacket(new ChunkPos(x, z), data);
    }

    public static void handle(FlowDirectionSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
            context.enqueueWork(() -> handleClient(msg));
        }
        context.setPacketHandled(true);
    }

    private static void handleClient(FlowDirectionSyncPacket msg) {
        FlowDirection[][] directions = decode(msg.data);
        FlowDirectionClientCache.put(msg.chunkPos, directions);
    }
}
