package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.EnumSet;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.client.ClientRegionCache;
import org.sosly.rivertale.client.RegionRenderer;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.networking.Message;
import org.sosly.rivertale.networking.Network;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.river.RiverRegion;
import org.sosly.rivertale.world.RiverShaping;

@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID)
public class VisCommand {
    private static final Map<UUID, EnumSet<VisMode>> ENABLED_MODES = new HashMap<>();
    private static final int REFRESH_TICKS = 20;
    private static final Map<UUID, Refresh> LAST_REFRESH = new HashMap<>();

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("vis")
            .executes(VisCommand::executeToggleAll)
            .then(Commands.literal("regions")
                .executes(ctx -> toggleMode(ctx, VisMode.REGIONS, "Region visualization")))
            .then(Commands.literal("cells")
                .executes(ctx -> toggleMode(ctx, VisMode.CELLS, "Cell visualization")))
            .then(Commands.literal("boundaries")
                .executes(ctx -> toggleMode(ctx, VisMode.BOUNDARIES, "Boundary visualization")));
    }

    private static int executeToggleAll(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        UUID playerId = player.getUUID();
        EnumSet<VisMode> modes = ENABLED_MODES.get(playerId);

        if (modes == null || modes.isEmpty()) {
            enableAll(player);
        } else if (modes.size() < VisMode.values().length) {
            enableAll(player);
        } else {
            disableAll(player);
        }

        return 1;
    }

    private static int toggleMode(CommandContext<CommandSourceStack> context, VisMode mode, String name) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        UUID playerId = player.getUUID();
        EnumSet<VisMode> modes = ENABLED_MODES.computeIfAbsent(playerId, k -> EnumSet.noneOf(VisMode.class));

        if (modes.contains(mode)) {
            modes.remove(mode);
            player.sendSystemMessage(Component.literal(name + " disabled.")
                .withStyle(ChatFormatting.YELLOW));
        } else {
            modes.add(mode);
            player.sendSystemMessage(Component.literal(name + " enabled.")
                .withStyle(ChatFormatting.GREEN));
        }

        if (modes.isEmpty()) {
            ENABLED_MODES.remove(playerId);
            LAST_REFRESH.remove(playerId);
        }

        sendUpdate(player);
        if (!modes.isEmpty() && !LAST_REFRESH.containsKey(playerId)) {
            sendRegions(player, new RegionPos(player.blockPosition()), player.serverLevel().getServer().getTickCount());
        }
        return 1;
    }

    private static void enableAll(ServerPlayer player) {
        UUID id = player.getUUID();
        ENABLED_MODES.put(id, EnumSet.allOf(VisMode.class));
        sendUpdate(player);

        sendRegions(player, new RegionPos(player.blockPosition()), player.serverLevel().getServer().getTickCount());

        player.sendSystemMessage(Component.literal("All visualizations enabled.")
            .withStyle(ChatFormatting.GREEN));
    }

    private static void disableAll(ServerPlayer player) {
        UUID id = player.getUUID();
        ENABLED_MODES.remove(id);
        LAST_REFRESH.remove(id);
        sendUpdate(player);

        player.sendSystemMessage(Component.literal("All visualizations disabled.")
            .withStyle(ChatFormatting.YELLOW));
    }

    private static void sendUpdate(ServerPlayer player) {
        EnumSet<VisMode> modes = ENABLED_MODES.getOrDefault(player.getUUID(), EnumSet.noneOf(VisMode.class));
        int seaLevel = player.serverLevel().getChunkSource().getGenerator().getSeaLevel();
        Network.sendToPlayer(new TogglePacket(modes, seaLevel), player);
    }

    private static void sendRegions(ServerPlayer player, RegionPos center, int tick) {
        LAST_REFRESH.put(player.getUUID(), new Refresh(center, tick));
        RandomState state = player.serverLevel().getChunkSource().randomState();
        for (RegionPos pos : center.neighborhood()) {
            RiverRegion region = RiverShaping.getReady(pos, state);
            if (region != null) {
                Network.sendToPlayer(new Region.Packet(region.encode()), player);
            }
        }
    }

    static void clearOverlays(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            if (isEnabled(player)) {
                LAST_REFRESH.remove(player.getUUID());
                Network.sendToPlayer(new ClearPacket(), player);
            }
        }
    }

    public static boolean isEnabled(ServerPlayer player) {
        EnumSet<VisMode> modes = ENABLED_MODES.get(player.getUUID());
        return modes != null && !modes.isEmpty();
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID id = player.getUUID();
        ENABLED_MODES.remove(id);
        LAST_REFRESH.remove(id);
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        UUID id = player.getUUID();
        if (ENABLED_MODES.remove(id) == null) {
            return;
        }
        LAST_REFRESH.remove(id);
        sendUpdate(player);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ENABLED_MODES.isEmpty()) {
            return;
        }

        MinecraftServer server = event.getServer();
        int tick = server.getTickCount();
        for (UUID id : new HashSet<>(ENABLED_MODES.keySet())) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                continue;
            }

            if (!RiverShaping.isActive(player.serverLevel().getChunkSource().randomState())) {
                ENABLED_MODES.remove(id);
                LAST_REFRESH.remove(id);
                sendUpdate(player);
                continue;
            }

            RegionPos current = new RegionPos(player.blockPosition());
            Refresh last = LAST_REFRESH.get(id);
            if (last == null || !current.equals(last.center()) || tick - last.tick() >= REFRESH_TICKS) {
                sendRegions(player, current, tick);
            }
        }
    }

    private record Refresh(RegionPos center, int tick) {
    }

    public static class ClearPacket extends Message {
        public static ClearPacket decode(FriendlyByteBuf buf) {
            return new ClearPacket();
        }

        public static void encode(ClearPacket msg, FriendlyByteBuf buf) {
        }

        public static void handle(ClearPacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> {
                    if (ClientRegionCache.isEnabled()) {
                        ClientRegionCache.get().clear();
                    }
                });
            }
            context.setPacketHandled(true);
        }
    }

    public static class TogglePacket extends Message {
        private final EnumSet<VisMode> modes;
        private final int seaLevel;

        public TogglePacket(Set<VisMode> modes, int seaLevel) {
            this.modes = modes.isEmpty()
                ? EnumSet.noneOf(VisMode.class)
                : EnumSet.copyOf(modes);
            this.seaLevel = seaLevel;
        }

        public static TogglePacket decode(FriendlyByteBuf buf) {
            int flags = buf.readInt();
            EnumSet<VisMode> modes = EnumSet.noneOf(VisMode.class);
            for (VisMode mode : VisMode.values()) {
                if ((flags & (1 << mode.ordinal())) == 0) {
                    continue;
                }
                modes.add(mode);
            }
            return new TogglePacket(modes, buf.readInt());
        }

        public static void encode(TogglePacket msg, FriendlyByteBuf buf) {
            int flags = 0;
            for (VisMode mode : VisMode.values()) {
                if (!msg.modes.contains(mode)) {
                    continue;
                }
                flags |= (1 << mode.ordinal());
            }
            buf.writeInt(flags);
            buf.writeInt(msg.seaLevel);
        }

        public static void handle(TogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> {
                    RegionRenderer.setSeaLevel(msg.modes.isEmpty() ? null : msg.seaLevel);
                    ClientRegionCache.setEnabledModes(msg.modes);
                });
            }
            context.setPacketHandled(true);
        }
    }
}
