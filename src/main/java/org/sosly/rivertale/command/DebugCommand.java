package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.river.WatershedCache;
import org.sosly.rivertale.RiverTale;

public class DebugCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("debug")
                .executes(DebugCommand::river);
    }

    private static int river(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        try {
            BlockPos pos = player.blockPosition();
            sendRiverDebugInfo(player, pos);
        } catch (Exception e) {
            RiverTale.LOGGER.error("Error executing debug river command", e);
            return 0;
        }

        return 1;
    }

    private static void sendRiverDebugInfo(ServerPlayer player, BlockPos pos) {
        CellPos cellPos = new CellPos(pos);
        Cell cell = CellCache.get().getOrCompute(cellPos);

        player.sendSystemMessage(Component.literal("=== River Debug at " + pos.getX() + ", " + pos.getZ() + " ==="));
        player.sendSystemMessage(Component.literal("Cell: " + cellPos + " (" + cell.feature().name() + ")"));

        String flowStr = cell.flowDirections().isEmpty()
            ? FlowDirection.NONE.name()
            : cell.flowDirections().get(0).name();
        player.sendSystemMessage(Component.literal("Flow: " + flowStr));

        player.sendSystemMessage(Component.literal("entryY: " + formatNullableInt(cell.entryY()) + ", exitY: " + formatNullableInt(cell.exitY())));
        player.sendSystemMessage(Component.literal("width: " + formatNullableInt(cell.width()) + ", depth: " + formatNullableInt(cell.depth())));
        player.sendSystemMessage(Component.literal("upstreamCount: " + formatNullableInt(cell.upstreamCount()) + ", downstreamCount: " + formatNullableInt(cell.downstreamCount())));
        player.sendSystemMessage(Component.literal("terminus: " + (cell.terminus() != null ? cell.terminus().toString() : "(not computed)")));

        Watershed watershed = WatershedCache.get().getWatershed(cellPos);
        if (watershed == null) {
            player.sendSystemMessage(Component.literal("Watershed: (none)"));
            return;
        }

        CellPos downstreamPos = watershed.downstream(cellPos);
        Set<CellPos> upstreamSet = watershed.upstream(cellPos);

        player.sendSystemMessage(Component.literal("Downstream: " + downstreamPos));
        player.sendSystemMessage(Component.literal("Upstream: " + upstreamSet.size() + " cells"));
        for (CellPos upstream : upstreamSet) {
            player.sendSystemMessage(Component.literal("  - " + upstream));
        }
    }

    private static String formatNullableInt(Integer value) {
        return value != null ? value.toString() : "(not computed)";
    }
}
