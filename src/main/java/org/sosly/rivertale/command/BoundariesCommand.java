package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ColumnPos;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;

public class BoundariesCommand {

    private static final int MAX_SAMPLES = 5;

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("boundaries")
            .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                .executes(BoundariesCommand::showBoundaries));
    }

    private static int showBoundaries(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        ColumnPos columnPos = ColumnPosArgument.getColumnPos(context, "pos");
        BlockPos blockPos = new BlockPos(columnPos.x(), 0, columnPos.z());
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

        List<Boundary> oceanBoundaries = new ArrayList<>();
        List<Boundary> basinBoundaries = new ArrayList<>();
        for (Boundary boundary : boundaries) {
            if (boundary.type() == BoundaryType.OCEAN) {
                oceanBoundaries.add(boundary);
            } else {
                basinBoundaries.add(boundary);
            }
        }

        int oceanCount = oceanBoundaries.size();
        int basinCount = basinBoundaries.size();
        source.sendSuccess(() -> Component.literal("  Ocean boundaries: " + oceanCount + " faces"), false);
        source.sendSuccess(() -> Component.literal("  Basin boundaries: " + basinCount + " faces"), false);

        if (!oceanBoundaries.isEmpty()) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("  Sample ocean boundaries:"), false);
            showSampleBoundaries(source, oceanBoundaries);
        }

        if (!basinBoundaries.isEmpty()) {
            source.sendSuccess(() -> Component.literal(""), false);
            source.sendSuccess(() -> Component.literal("  Sample basin boundaries:"), false);
            showSampleBoundaries(source, basinBoundaries);
        }

        return 1;
    }

    private static void showSampleBoundaries(CommandSourceStack source, List<Boundary> boundaries) {
        int samplesToShow = Math.min(MAX_SAMPLES, boundaries.size());
        for (int i = 0; i < samplesToShow; i++) {
            Boundary boundary = boundaries.get(i);
            source.sendSuccess(() -> Component.literal(
                "    CellPos" + boundary.land() + " -> CellPos" + boundary.water() + " [" + boundary.direction() + "]"
            ), false);
        }
        if (boundaries.size() > MAX_SAMPLES) {
            int remaining = boundaries.size() - MAX_SAMPLES;
            source.sendSuccess(() -> Component.literal("    ... (" + remaining + " more)"), false);
        }
    }
}
