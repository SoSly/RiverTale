package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.client.RegionCache;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.networking.Message;
import org.sosly.rivertale.networking.Network;
import org.sosly.rivertale.region.Region;

@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID)
public class VisCommand {
    private static final Set<UUID> ENABLED_PLAYERS = new HashSet<>();
    private static final Map<UUID, RegionPos> LAST_REGION = new HashMap<>();

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("vis")
            .executes(VisCommand::execute);
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        UUID playerId = player.getUUID();
        if (ENABLED_PLAYERS.contains(playerId)) {
            disable(player);
        } else {
            enable(player);
        }

        return 1;
    }

    private static void enable(ServerPlayer player) {
        UUID id = player.getUUID();
        ENABLED_PLAYERS.add(id);
        Network.sendToPlayer(new TogglePacket(true), player);

        RegionPos center = new RegionPos(player.blockPosition());
        LAST_REGION.put(id, center);
        sendRegions(player, center);

        player.sendSystemMessage(Component.literal("Visualization enabled.")
            .withStyle(ChatFormatting.GREEN));
    }

    private static void disable(ServerPlayer player) {
        UUID id = player.getUUID();
        ENABLED_PLAYERS.remove(id);
        LAST_REGION.remove(id);
        Network.sendToPlayer(new TogglePacket(false), player);

        player.sendSystemMessage(Component.literal("Visualization disabled.")
            .withStyle(ChatFormatting.YELLOW));
    }

    private static void sendRegions(ServerPlayer player, RegionPos center) {
        CellCache cellCache = CellCache.get();

        sendRegion(player, center, cellCache);
        for (Direction dir : Direction.D8) {
            sendRegion(player, center.relative(dir), cellCache);
        }
    }

    private static void sendRegion(ServerPlayer player, RegionPos pos, CellCache cellCache) {
        Region region = Region.create(pos, cellCache);
        Network.sendToPlayer(new Region.Packet(region), player);
    }

    public static boolean isEnabled(ServerPlayer player) {
        return ENABLED_PLAYERS.contains(player.getUUID());
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID id = player.getUUID();
            ENABLED_PLAYERS.remove(id);
            LAST_REGION.remove(id);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ENABLED_PLAYERS.isEmpty()) {
            return;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        for (UUID id : ENABLED_PLAYERS) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }

            RegionPos current = new RegionPos(player.blockPosition());
            RegionPos last = LAST_REGION.get(id);

            if (last == null || !current.equals(last)) {
                LAST_REGION.put(id, current);
                sendRegions(player, current);
            }
        }
    }

    public static class TogglePacket extends Message {
        private final boolean enabled;

        public TogglePacket(boolean enabled) {
            this.enabled = enabled;
        }

        public static TogglePacket decode(FriendlyByteBuf buf) {
            return new TogglePacket(buf.readBoolean());
        }

        public static void encode(TogglePacket msg, FriendlyByteBuf buf) {
            buf.writeBoolean(msg.enabled);
        }

        public static void handle(TogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> RegionCache.setEnabled(msg.enabled));
            }
            context.setPacketHandled(true);
        }
    }
}
