package org.sosly.rivertale.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;

public class BoundariesCommand {

    private static final int MAX_CELLS_TO_LIST = 5;

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("boundaries")
            .then(Commands.argument("x", IntegerArgumentType.integer())
                .then(Commands.argument("z", IntegerArgumentType.integer())
                    .executes(BoundariesCommand::showBoundaries)));
    }

    private static int showBoundaries(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        int x = IntegerArgumentType.getInteger(context, "x");
        int z = IntegerArgumentType.getInteger(context, "z");
        BlockPos blockPos = new BlockPos(x, 0, z);
        RegionPos regionPos = new RegionPos(blockPos);

        Region region = RegionCache.get().getOrCompute(regionPos);

        source.sendSuccess(() -> Component.literal(
            "Boundaries for RegionPos" + regionPos + " - " + region.type().name() + ":"
        ), false);

        List<Boundary> boundaries = region.boundaries();
        if (boundaries.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  (none)"), false);
            return 1;
        }

        for (Boundary boundary : boundaries) {
            source.sendSuccess(() -> Component.literal(""), false);
            formatBoundary(source, boundary);
        }

        return 1;
    }

    private static void formatBoundary(CommandSourceStack source, Boundary boundary) {
        String typeName = boundary.type() == BoundaryType.OCEAN ? "Ocean" : "Basin";
        List<CellPos> cells = boundary.cells();
        int cellCount = cells.size();

        source.sendSuccess(() -> Component.literal(
            "  " + typeName + " boundary: " + cellCount + " cells"
        ), false);

        if (cellCount == 0) {
            return;
        }

        if (boundary.type() == BoundaryType.OCEAN) {
            formatOceanBoundary(source, cells);
        } else {
            formatBasinBoundary(source, cells);
        }
    }

    private static void formatOceanBoundary(CommandSourceStack source, List<CellPos> cells) {
        CellPos minCorner = findMinCorner(cells);
        CellPos maxCorner = findMaxCorner(cells);

        source.sendSuccess(() -> Component.literal(
            "    Northwest corner: CellPos" + minCorner
        ), false);
        source.sendSuccess(() -> Component.literal(
            "    Southeast corner: CellPos" + maxCorner
        ), false);
    }

    private static void formatBasinBoundary(CommandSourceStack source, List<CellPos> cells) {
        if (cells.size() <= MAX_CELLS_TO_LIST) {
            for (CellPos cell : cells) {
                source.sendSuccess(() -> Component.literal(
                    "    CellPos" + cell
                ), false);
            }
        } else {
            CellPos first = cells.get(0);
            CellPos last = cells.get(cells.size() - 1);
            source.sendSuccess(() -> Component.literal(
                "    CellPos" + first
            ), false);
            source.sendSuccess(() -> Component.literal(
                "    ... (" + (cells.size() - 2) + " more)"
            ), false);
            source.sendSuccess(() -> Component.literal(
                "    CellPos" + last
            ), false);
        }
    }

    private static CellPos findMinCorner(List<CellPos> cells) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        for (CellPos cell : cells) {
            if (cell.x() < minX) {
                minX = cell.x();
            }
            if (cell.z() < minZ) {
                minZ = cell.z();
            }
        }
        return new CellPos(minX, minZ);
    }

    private static CellPos findMaxCorner(List<CellPos> cells) {
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (CellPos cell : cells) {
            if (cell.x() > maxX) {
                maxX = cell.x();
            }
            if (cell.z() > maxZ) {
                maxZ = cell.z();
            }
        }
        return new CellPos(maxX, maxZ);
    }
}
