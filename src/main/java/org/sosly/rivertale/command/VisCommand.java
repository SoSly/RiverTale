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
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.client.ClientRegionCache;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.networking.Message;
import org.sosly.rivertale.networking.Network;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.river.WatershedCache;

@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID)
public class VisCommand {
    private static final Map<UUID, EnumSet<VisMode>> ENABLED_MODES = new HashMap<>();
    private static final Map<UUID, RegionPos> LAST_REGION = new HashMap<>();

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
            LAST_REGION.remove(playerId);
            sendUpdate(player);
            return 1;
        }

        if (!LAST_REGION.containsKey(playerId)) {
            RegionPos center = new RegionPos(player.blockPosition());
            LAST_REGION.put(playerId, center);
            sendRegions(player, center);
        }

        sendUpdate(player);
        return 1;
    }

    private static void enableAll(ServerPlayer player) {
        UUID id = player.getUUID();
        ENABLED_MODES.put(id, EnumSet.allOf(VisMode.class));
        sendUpdate(player);

        RegionPos center = new RegionPos(player.blockPosition());
        LAST_REGION.put(id, center);
        sendRegions(player, center);

        player.sendSystemMessage(Component.literal("All visualizations enabled.")
            .withStyle(ChatFormatting.GREEN));
    }

    private static void disableAll(ServerPlayer player) {
        UUID id = player.getUUID();
        ENABLED_MODES.remove(id);
        LAST_REGION.remove(id);
        sendUpdate(player);

        player.sendSystemMessage(Component.literal("All visualizations disabled.")
            .withStyle(ChatFormatting.YELLOW));
    }

    private static void sendUpdate(ServerPlayer player) {
        EnumSet<VisMode> modes = ENABLED_MODES.getOrDefault(player.getUUID(), EnumSet.noneOf(VisMode.class));
        Network.sendToPlayer(new TogglePacket(modes), player);
    }

    private static void sendRegions(ServerPlayer player, RegionPos center) {
        RegionCache regionCache = RegionCache.get();

        Set<RegionPos> loadedRegions = new HashSet<>();

        regionCache.getOrCompute(center);
        loadedRegions.add(center);

        for (FlowDirection dir : FlowDirection.D8) {
            RegionPos neighbor = center.relative(dir);
            regionCache.getOrCompute(neighbor);
            loadedRegions.add(neighbor);
        }

        WatershedCache.get().finalizeReady(loadedRegions);

        for (RegionPos pos : loadedRegions) {
            Region region = regionCache.getOrCompute(pos);
            Network.sendToPlayer(new Region.Packet(region), player);
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
        LAST_REGION.remove(id);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (ENABLED_MODES.isEmpty()) {
            return;
        }

        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }

        for (UUID id : ENABLED_MODES.keySet()) {
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
        private final EnumSet<VisMode> modes;

        public TogglePacket(Set<VisMode> modes) {
            this.modes = modes.isEmpty()
                ? EnumSet.noneOf(VisMode.class)
                : EnumSet.copyOf(modes);
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
            return new TogglePacket(modes);
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
        }

        public static void handle(TogglePacket msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> ClientRegionCache.setEnabledModes(msg.modes));
            }
            context.setPacketHandled(true);
        }
    }
}
