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
import org.sosly.rivertale.worldgen.river.ParticipationCalculator;
import org.sosly.rivertale.worldgen.river.RiverCellKey;

public class CellCommand {

    private static final double OCEAN_THRESHOLD = -0.13;

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
                    int cellSize = RiverCellKey.getCellSize(currentPass);

                    boolean participating = ParticipationCalculator.isParticipating(key, worldSeed);

                    source.sendSuccess(() -> Component.literal("-----").withStyle(ChatFormatting.GRAY), false);
                    source.sendSuccess(() -> Component.literal(String.format("Pass %d - Cell (%d, %d):", currentPass, key.cellX(), key.cellZ()))
                        .withStyle(ChatFormatting.YELLOW), false);

                    if (!participating) {
                        source.sendSuccess(() -> Component.literal("  Participating: false")
                            .withStyle(ChatFormatting.WHITE), false);
                        continue;
                    }

                    double[][] subcells = provider.sampleSubcellDensities(key.worldX(), key.worldZ(), cellSize);
                    double avgDensity = provider.getAveragedDensity(key.worldX(), key.worldZ(), cellSize);
                    CellClassification classification = classify(subcells, OCEAN_THRESHOLD);

                    if (classification == CellClassification.OCEAN || classification == CellClassification.COASTAL) {
                        source.sendSuccess(() -> Component.literal(String.format("  Center: (%,d, %,d)", key.centerX(), key.centerZ()))
                            .withStyle(ChatFormatting.WHITE), false);
                        source.sendSuccess(() -> Component.literal(String.format("  Density: %.3f", avgDensity))
                            .withStyle(ChatFormatting.WHITE), false);
                        source.sendSuccess(() -> Component.literal(String.format("  Classification: %s", classification))
                            .withStyle(ChatFormatting.WHITE), false);
                        continue;
                    }

                    source.sendSuccess(() -> Component.literal(String.format("  Center: (%,d, %,d)", key.centerX(), key.centerZ()))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Density: %.3f", avgDensity))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Classification: %s", classification))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal("  Participating: true")
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal("  Output: NONE [not computed]")
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal("  Distance to ocean: -1 [not computed]")
                        .withStyle(ChatFormatting.WHITE), false);
                }

                return 1;
            });
    }

    private static CellClassification classify(double[][] subcells, double oceanThreshold) {
        boolean hasLand = false;
        boolean hasOcean = false;

        for (int row = 0; row < 7; row++) {
            for (int col = 0; col < 7; col++) {
                if (subcells[row][col] >= oceanThreshold) {
                    hasLand = true;
                } else {
                    hasOcean = true;
                }
            }
        }

        if (hasLand && hasOcean) {
            return CellClassification.COASTAL;
        }
        if (hasOcean) {
            return CellClassification.OCEAN;
        }
        return CellClassification.LAND;
    }
}
