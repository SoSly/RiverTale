package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.worldgen.river.CellClassification;
import org.sosly.rivertale.worldgen.river.CellDensityProvider;
import org.sosly.rivertale.worldgen.river.PathDirection;
import org.sosly.rivertale.worldgen.river.RiverCell;
import org.sosly.rivertale.worldgen.river.RiverCellCache;
import org.sosly.rivertale.worldgen.river.RiverCellKey;
import org.sosly.rivertale.worldgen.river.RiverCellManager;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CellCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("cell")
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                ServerLevel level = source.getLevel();
                BlockPos pos = BlockPos.containing(source.getPosition());
                RandomState randomState = level.getChunkSource().randomState();

                CellDensityProvider provider = new CellDensityProvider(randomState);

                RiverCellKey key = RiverCellKey.fromBlockPos(pos.getX(), pos.getZ());
                boolean wasCached = RiverCellCache.getIfPresent(key) != null;
                RiverCell cell = RiverCellManager.getOrCreate(key, provider, randomState);

                String cacheStatus = wasCached ? "[cached]" : "[computed]";
                source.sendSuccess(() -> Component.literal("-----").withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(String.format("Cell (%d, %d): %s", key.cellX(), key.cellZ(), cacheStatus))
                    .withStyle(ChatFormatting.YELLOW), false);

                if (!cell.isParticipating()) {
                    source.sendSuccess(() -> Component.literal("  Participating: false")
                        .withStyle(ChatFormatting.WHITE), false);
                    return 1;
                }

                CellClassification classification = cell.getClassification();

                boolean isTerminus = classification == CellClassification.OCEAN
                        || classification == CellClassification.COASTAL
                        || classification == CellClassification.LAKE
                        || classification == CellClassification.LAKESHORE;

                if (isTerminus) {
                    source.sendSuccess(() -> Component.literal(String.format("  Center: (%,d, %,d)", key.centerX(), key.centerZ()))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Density: %.3f", cell.getDensity()))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Classification: %s", classification))
                        .withStyle(ChatFormatting.WHITE), false);

                    int distance = RiverCellManager.getDistanceToTerminus(cell, provider, randomState);
                    source.sendSuccess(() -> Component.literal(String.format("  Distance to terminus: %d", distance))
                        .withStyle(ChatFormatting.WHITE), false);

                    if (classification == CellClassification.COASTAL || classification == CellClassification.LAKESHORE) {
                        int upstreamCount = RiverCellManager.getUpstreamCount(key, provider, randomState);
                        source.sendSuccess(() -> Component.literal(String.format("  Upstream count: %d", upstreamCount))
                            .withStyle(ChatFormatting.WHITE), false);
                    }

                    RiverCellManager.ensurePaths(cell, provider, randomState);
                    Map<PathDirection, List<int[]>> terminusPaths = cell.getRiverPaths();
                    if (!terminusPaths.isEmpty()) {
                        for (Map.Entry<PathDirection, List<int[]>> entry : terminusPaths.entrySet()) {
                            PathDirection inputDirection = entry.getKey();
                            List<int[]> path = entry.getValue();
                            String pathStr = path.stream()
                                .map(coords -> String.format("(%d,%d)", coords[0], coords[1]))
                                .collect(Collectors.joining(" -> "));
                            source.sendSuccess(() -> Component.literal(String.format("  Path from %s: %s", inputDirection, pathStr))
                                .withStyle(ChatFormatting.WHITE), false);
                        }
                    }
                    return 1;
                }

                source.sendSuccess(() -> Component.literal(String.format("  Center: (%,d, %,d)", key.centerX(), key.centerZ()))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Density: %.3f", cell.getDensity()))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Classification: %s", classification))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal("  Participating: true")
                    .withStyle(ChatFormatting.WHITE), false);

                PathDirection primaryOutput = cell.getPrimaryOutput();
                source.sendSuccess(() -> Component.literal(String.format("  Output: %s", primaryOutput))
                    .withStyle(ChatFormatting.WHITE), false);

                Set<PathDirection> secondaryOutputs = cell.getSecondaryOutputs();
                if (!secondaryOutputs.isEmpty()) {
                    String secondaryStr = secondaryOutputs.stream()
                        .map(PathDirection::toString)
                        .sorted()
                        .collect(Collectors.joining(", "));
                    source.sendSuccess(() -> Component.literal(String.format("  Secondary outputs: %s", secondaryStr))
                        .withStyle(ChatFormatting.WHITE), false);
                }

                if (classification == CellClassification.LAND) {
                    source.sendSuccess(() -> Component.literal(String.format("  Basin: %s", cell.isBasin()))
                        .withStyle(ChatFormatting.WHITE), false);
                }

                int distance = RiverCellManager.getDistanceToTerminus(cell, provider, randomState);
                source.sendSuccess(() -> Component.literal(String.format("  Distance to terminus: %d", distance))
                    .withStyle(ChatFormatting.WHITE), false);

                if (classification == CellClassification.LAND) {
                    int upstreamCount = RiverCellManager.getUpstreamCount(key, provider, randomState);
                    source.sendSuccess(() -> Component.literal(String.format("  Upstream count: %d", upstreamCount))
                        .withStyle(ChatFormatting.WHITE), false);
                }

                RiverCellManager.ensurePaths(cell, provider, randomState);
                Map<PathDirection, List<int[]>> paths = cell.getRiverPaths();
                if (!paths.isEmpty()) {
                    for (Map.Entry<PathDirection, List<int[]>> entry : paths.entrySet()) {
                        PathDirection inputDirection = entry.getKey();
                        List<int[]> path = entry.getValue();
                        String pathStr = path.stream()
                            .map(coords -> String.format("(%d,%d)", coords[0], coords[1]))
                            .collect(Collectors.joining(" -> "));
                        String label = formatPathLabel(inputDirection, primaryOutput, cell);
                        source.sendSuccess(() -> Component.literal(String.format("  %s: %s", label, pathStr))
                            .withStyle(ChatFormatting.WHITE), false);
                    }
                }

                return 1;
            });
    }

    private static String formatPathLabel(PathDirection key, PathDirection primaryOutput, RiverCell cell) {
        if (key == primaryOutput && !isEdgeDirection(key, cell)) {
            return String.format("Source to %s", primaryOutput);
        }

        if (cell.getSecondaryOutputs().contains(key)) {
            return String.format("Secondary source to %s", key);
        }

        return String.format("Path from %s to %s", key, primaryOutput);
    }

    private static boolean isEdgeDirection(PathDirection key, RiverCell cell) {
        List<int[]> path = cell.getRiverPaths().get(key);
        if (path == null || path.isEmpty()) {
            return false;
        }
        int[] start = path.get(0);
        int row = start[0];
        int col = start[1];
        return row == 0 || row == 7 || col == 0 || col == 7;
    }
}
