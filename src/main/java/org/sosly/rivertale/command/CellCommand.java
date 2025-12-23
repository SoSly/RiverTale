package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellClassifier;
import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.path.Manager;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.RegionDensityProvider;

public class CellCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("cell")
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                ServerLevel level = source.getLevel();
                BlockPos pos = BlockPos.containing(source.getPosition());
                RandomState randomState = level.getChunkSource().randomState();

                RegionDensityProvider provider = new RegionDensityProvider(randomState);

                int playerX = pos.getX();
                int playerZ = pos.getZ();

                RegionPos regionKey = RegionPos.at(playerX, playerZ);
                Region region = Manager.createRegionFor(regionKey, provider, randomState);
                java.util.Map<Direction, java.util.List<CellPos>> paths = Manager.computePaths(region, provider, randomState);

                int regionSize = RegionPos.getRegionSize();
                int cellSpacing = regionSize / 8;

                int localX = Math.floorMod(playerX - regionKey.worldX(), regionSize);
                int localZ = Math.floorMod(playerZ - regionKey.worldZ(), regionSize);
                int col = localX / cellSpacing;
                int row = localZ / cellSpacing;

                int cellCenterX = regionKey.worldX() + col * cellSpacing + cellSpacing / 2;
                int cellCenterZ = regionKey.worldZ() + row * cellSpacing + cellSpacing / 2;

                Cell cell = region.cells().get(row, col);
                Direction flowDirection = cell.flowDirection();
                RegionType terrain = Manager.classifyTerrain(regionKey, provider);

                CellType featureCellType = CellClassifier.classify(cell, region, paths, terrain);

                source.sendSuccess(() -> Component.literal("-----").withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(String.format("Cell (%d, %d) in Region (%d, %d)", row, col, regionKey.x(), regionKey.z()))
                    .withStyle(ChatFormatting.YELLOW), false);
                source.sendSuccess(() -> Component.literal(String.format("  World position: (%,d, %,d)", cellCenterX, cellCenterZ))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Flow Direction: %s", flowDirection))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Cell CellType: %s", featureCellType))
                    .withStyle(ChatFormatting.WHITE), false);

                double continents = provider.getContinents(cellCenterX, cellCenterZ);
                double depth = provider.getDepth(cellCenterX, cellCenterZ);
                double combined = provider.getDensity(cellCenterX, cellCenterZ);

                source.sendSuccess(() -> Component.literal(String.format("  Continents: %.4f", continents))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Depth: %.4f", depth))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Combined: %.4f", combined))
                    .withStyle(ChatFormatting.WHITE), false);

                return 1;
            });
    }
}
