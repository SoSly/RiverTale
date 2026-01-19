package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionExplorer;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.RiverTale;

public class RegionSetCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("regionset")
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(RegionSetCommand::execute));
    }

    private static int execute(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        try {
            ColumnPos columnPos = ColumnPosArgument.getColumnPos(context, "pos");
            int x = columnPos.x();
            int z = columnPos.z();
            BlockPos blockPos = new BlockPos(x, 0, z);
            RegionPos centerPos = new RegionPos(blockPos);

            Set<Region> workingSet = RegionExplorer.discover(blockPos);
            Region centerRegion = RegionCache.get().getOrCompute(centerPos);

            source.sendSuccess(() -> Component.literal("Region Set for (" + x + ", " + z + "):"), false);
            source.sendSuccess(() -> Component.literal("  Center: RegionPos" + centerPos + " - " + centerRegion.type().name()), false);
            source.sendSuccess(() -> Component.literal("  Working set size: " + workingSet.size()), false);

            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("  Included regions:"), false);

            for (Region region : workingSet) {
                String suffix = buildIncludedSuffix(region, centerRegion);
                source.sendSuccess(() -> Component.literal("    - RegionPos" + region.pos() + " " + region.type().name() + suffix), false);
            }

            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("  Excluded neighbors:"), false);

            for (FlowDirection dir : FlowDirection.D4) {
                RegionPos neighborPos = centerPos.relative(dir);
                boolean inWorkingSet = workingSet.stream().anyMatch(r -> r.pos().equals(neighborPos));

                if (!inWorkingSet) {
                    Region neighbor = RegionCache.get().getOrCompute(neighborPos);
                    String dirName = directionName(dir);
                    source.sendSuccess(() -> Component.literal("    - RegionPos" + neighborPos + " " + neighbor.type().name() + " (" + dirName + ")"), false);
                }
            }
        } catch (Exception e) {
            RiverTale.LOGGER.error("Error executing regionset command", e);
            source.sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }

        return 1;
    }

    private static String buildIncludedSuffix(Region region, Region centerRegion) {
        if (region.pos().equals(centerRegion.pos())) {
            return " (center)";
        }

        FlowDirection dirFromCenter = findDirection(centerRegion.pos(), region.pos());
        if (dirFromCenter == FlowDirection.NONE) {
            return "";
        }

        String dirName = directionName(dirFromCenter);
        String flowDesc = buildFlowDescription(centerRegion, region, dirFromCenter);
        return " (" + dirName + ", " + flowDesc + ")";
    }

    private static String buildFlowDescription(Region center, Region neighbor, FlowDirection dirFromCenter) {
        if (center.type() == RegionType.COASTAL) {
            return "flows " + directionName(dirFromCenter.opposite());
        }
        return "drains " + directionName(dirFromCenter);
    }

    private static FlowDirection findDirection(RegionPos from, RegionPos to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();

        for (FlowDirection dir : FlowDirection.D4) {
            if (dir.dx == dx && dir.dz == dz) {
                return dir;
            }
        }
        return FlowDirection.NONE;
    }

    private static String directionName(FlowDirection dir) {
        return switch (dir) {
            case NORTH -> "north";
            case SOUTH -> "south";
            case EAST -> "east";
            case WEST -> "west";
            default -> dir.name().toLowerCase();
        };
    }
}
