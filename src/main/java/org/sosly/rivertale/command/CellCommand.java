package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.worldgen.river.CellFeatureType;
import org.sosly.rivertale.worldgen.river.D8FlowCalculator;
import org.sosly.rivertale.worldgen.river.FlowDirection;
import org.sosly.rivertale.worldgen.river.RegionDensityProvider;
import org.sosly.rivertale.worldgen.river.RiverRegion;
import org.sosly.rivertale.worldgen.river.RiverRegionKey;
import org.sosly.rivertale.worldgen.river.RiverRegionManager;

import java.util.function.BiFunction;

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

                RiverRegionKey regionKey = RiverRegionKey.fromBlockPos(playerX, playerZ);
                RiverRegion region = RiverRegionManager.getOrCreate(regionKey, provider, randomState);
                RiverRegionManager.ensurePaths(region, provider, randomState);

                int regionSize = RiverRegionKey.getRegionSize();
                int cellSpacing = regionSize / 8;

                int localX = Math.floorMod(playerX - regionKey.worldX(), regionSize);
                int localZ = Math.floorMod(playerZ - regionKey.worldZ(), regionSize);
                int col = localX / cellSpacing;
                int row = localZ / cellSpacing;

                int cellCenterX = regionKey.worldX() + col * cellSpacing + cellSpacing / 2;
                int cellCenterZ = regionKey.worldZ() + row * cellSpacing + cellSpacing / 2;

                BiFunction<Integer, Integer, Double> densitySampler = provider::getDensity;
                FlowDirection[][] flowDirections = D8FlowCalculator.computeFlowDirections(regionKey, densitySampler);
                FlowDirection flowDirection = flowDirections[row][col];

                CellFeatureType featureType = RiverRegionManager.getCellFeatureType(region, row, col, provider);

                source.sendSuccess(() -> Component.literal("-----").withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(String.format("Cell (%d, %d) in Region (%d, %d)", row, col, regionKey.regionX(), regionKey.regionZ()))
                    .withStyle(ChatFormatting.YELLOW), false);
                source.sendSuccess(() -> Component.literal(String.format("  World position: (%,d, %,d)", cellCenterX, cellCenterZ))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Flow Direction: %s", flowDirection))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Cell Type: %s", featureType))
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
