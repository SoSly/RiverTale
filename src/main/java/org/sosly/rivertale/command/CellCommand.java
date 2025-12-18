package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import org.sosly.rivertale.worldgen.river.CellClassification;
import org.sosly.rivertale.worldgen.river.ContinentsDensityProvider;
import org.sosly.rivertale.worldgen.river.FlowDirection;
import org.sosly.rivertale.worldgen.river.RiverCell;
import org.sosly.rivertale.worldgen.river.RiverCellKey;
import org.sosly.rivertale.worldgen.river.RiverCellManager;

import java.util.Set;
import java.util.stream.Collectors;

public class CellCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("cell")
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                ServerLevel level = source.getLevel();
                BlockPos pos = BlockPos.containing(source.getPosition());
                long worldSeed = level.getSeed();

                ContinentsDensityProvider provider = new ContinentsDensityProvider(level);

                for (int pass = 1; pass <= 2; pass++) {
                    final int currentPass = pass;
                    RiverCellKey key = RiverCellKey.fromBlockPos(pos.getX(), pos.getZ(), currentPass);
                    RiverCell cell = RiverCellManager.createCell(key, provider, worldSeed);

                    source.sendSuccess(() -> Component.literal("-----").withStyle(ChatFormatting.GRAY), false);
                    source.sendSuccess(() -> Component.literal(String.format("Pass %d - Cell (%d, %d):", currentPass, key.cellX(), key.cellZ()))
                        .withStyle(ChatFormatting.YELLOW), false);

                    if (!cell.isParticipating()) {
                        source.sendSuccess(() -> Component.literal("  Participating: false")
                            .withStyle(ChatFormatting.WHITE), false);
                        continue;
                    }

                    CellClassification classification = cell.getClassification();

                    if (classification == CellClassification.OCEAN || classification == CellClassification.COASTAL) {
                        source.sendSuccess(() -> Component.literal(String.format("  Center: (%,d, %,d)", key.centerX(), key.centerZ()))
                            .withStyle(ChatFormatting.WHITE), false);
                        source.sendSuccess(() -> Component.literal(String.format("  Density: %.3f", cell.getDensity()))
                            .withStyle(ChatFormatting.WHITE), false);
                        source.sendSuccess(() -> Component.literal(String.format("  Classification: %s", classification))
                            .withStyle(ChatFormatting.WHITE), false);
                        continue;
                    }

                    source.sendSuccess(() -> Component.literal(String.format("  Center: (%,d, %,d)", key.centerX(), key.centerZ()))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Density: %.3f", cell.getDensity()))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Classification: %s", classification))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal("  Participating: true")
                        .withStyle(ChatFormatting.WHITE), false);

                    FlowDirection primaryOutput = cell.getPrimaryOutput();
                    source.sendSuccess(() -> Component.literal(String.format("  Output: %s", primaryOutput))
                        .withStyle(ChatFormatting.WHITE), false);

                    Set<FlowDirection> secondaryOutputs = cell.getSecondaryOutputs();
                    if (!secondaryOutputs.isEmpty()) {
                        String secondaryStr = secondaryOutputs.stream()
                            .map(FlowDirection::toString)
                            .sorted()
                            .collect(Collectors.joining(", "));
                        source.sendSuccess(() -> Component.literal(String.format("  Secondary outputs: %s", secondaryStr))
                            .withStyle(ChatFormatting.WHITE), false);
                    }

                    if (classification == CellClassification.LAND) {
                        source.sendSuccess(() -> Component.literal(String.format("  Basin: %s", cell.isBasin()))
                            .withStyle(ChatFormatting.WHITE), false);
                    }

                    source.sendSuccess(() -> Component.literal("  Distance to ocean: -1 [Phase 6]")
                        .withStyle(ChatFormatting.WHITE), false);
                }

                return 1;
            });
    }
}
