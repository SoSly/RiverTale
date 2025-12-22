package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.worldgen.river.PathDirection;
import org.sosly.rivertale.worldgen.river.RegionDensityProvider;
import org.sosly.rivertale.worldgen.river.RegionFeatureType;
import org.sosly.rivertale.worldgen.river.RiverRegion;
import org.sosly.rivertale.worldgen.river.RiverRegionKey;
import org.sosly.rivertale.worldgen.river.RiverRegionManager;

import java.util.ArrayList;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class RegionCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("region")
            .executes(context -> {
                CommandSourceStack source = context.getSource();
                ServerLevel level = source.getLevel();
                BlockPos pos = BlockPos.containing(source.getPosition());
                RandomState randomState = level.getChunkSource().randomState();

                RegionDensityProvider provider = new RegionDensityProvider(randomState);

                RiverRegionKey key = RiverRegionKey.fromBlockPos(pos.getX(), pos.getZ());
                RiverRegion region = RiverRegionManager.createRegionFor(key, provider, randomState);

                source.sendSuccess(() -> Component.literal("-----").withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(String.format("Region (%d, %d)", key.regionX(), key.regionZ()))
                    .withStyle(ChatFormatting.YELLOW), false);

                RegionFeatureType featureType = RiverRegionManager.getRegionFeatureType(region, provider, randomState);

                if (!region.isParticipating()) {
                    source.sendSuccess(() -> Component.literal("  Participating: false")
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Region Type: %s", featureType))
                        .withStyle(ChatFormatting.WHITE), false);
                    return 1;
                }

                boolean isTerminus = featureType == RegionFeatureType.BODY || featureType == RegionFeatureType.SHORE;

                if (isTerminus) {
                    source.sendSuccess(() -> Component.literal(String.format("  Center: (%,d, %,d)", key.centerX(), key.centerZ()))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Density: %.3f", region.getDensity()))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Region Type: %s", featureType))
                        .withStyle(ChatFormatting.WHITE), false);

                    int distance = RiverRegionManager.getDistanceToTerminus(region, provider, randomState);
                    source.sendSuccess(() -> Component.literal(String.format("  Distance to terminus: %d", distance))
                        .withStyle(ChatFormatting.WHITE), false);

                    if (featureType == RegionFeatureType.SHORE) {
                        int upstreamCount = RiverRegionManager.getUpstreamCount(key, provider, randomState);
                        source.sendSuccess(() -> Component.literal(String.format("  Upstream count: %d", upstreamCount))
                            .withStyle(ChatFormatting.WHITE), false);

                        reportWaterSamples(source, key, provider);
                    }

                    Map<PathDirection, List<int[]>> terminusPaths = RiverRegionManager.computePaths(region, provider, randomState);
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
                source.sendSuccess(() -> Component.literal(String.format("  Density: %.3f", region.getDensity()))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Region Type: %s", featureType))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal("  Participating: true")
                    .withStyle(ChatFormatting.WHITE), false);

                PathDirection primaryOutput = region.getPrimaryOutput();
                source.sendSuccess(() -> Component.literal(String.format("  Output: %s", primaryOutput))
                    .withStyle(ChatFormatting.WHITE), false);

                Set<PathDirection> secondaryOutputs = region.getSecondaryOutputs();
                if (!secondaryOutputs.isEmpty()) {
                    String secondaryStr = secondaryOutputs.stream()
                        .map(PathDirection::toString)
                        .sorted()
                        .collect(Collectors.joining(", "));
                    source.sendSuccess(() -> Component.literal(String.format("  Secondary outputs: %s", secondaryStr))
                        .withStyle(ChatFormatting.WHITE), false);
                }

                int distance = RiverRegionManager.getDistanceToTerminus(region, provider, randomState);
                source.sendSuccess(() -> Component.literal(String.format("  Distance to terminus: %d", distance))
                    .withStyle(ChatFormatting.WHITE), false);

                int upstreamCount = RiverRegionManager.getUpstreamCount(key, provider, randomState);
                source.sendSuccess(() -> Component.literal(String.format("  Upstream count: %d", upstreamCount))
                    .withStyle(ChatFormatting.WHITE), false);

                Map<PathDirection, List<int[]>> paths = RiverRegionManager.computePaths(region, provider, randomState);
                if (!paths.isEmpty()) {
                    for (Map.Entry<PathDirection, List<int[]>> entry : paths.entrySet()) {
                        PathDirection inputDirection = entry.getKey();
                        List<int[]> path = entry.getValue();
                        String pathStr = path.stream()
                            .map(coords -> String.format("(%d,%d)", coords[0], coords[1]))
                            .collect(Collectors.joining(" -> "));
                        String label = formatPathLabel(inputDirection, primaryOutput, region, paths);
                        source.sendSuccess(() -> Component.literal(String.format("  %s: %s", label, pathStr))
                            .withStyle(ChatFormatting.WHITE), false);
                    }
                }

                return 1;
            });
    }

    private static String formatPathLabel(PathDirection key, PathDirection primaryOutput, RiverRegion region, Map<PathDirection, List<int[]>> paths) {
        if (key == primaryOutput && !isEdgeDirection(key, paths)) {
            return String.format("Source to %s", primaryOutput);
        }

        if (region.getSecondaryOutputs().contains(key)) {
            return String.format("Secondary source to %s", key);
        }

        return String.format("Path from %s to %s", key, primaryOutput);
    }

    private static boolean isEdgeDirection(PathDirection key, Map<PathDirection, List<int[]>> paths) {
        List<int[]> path = paths.get(key);
        if (path == null || path.isEmpty()) {
            return false;
        }
        int[] start = path.get(0);
        int row = start[0];
        int col = start[1];
        return row == 0 || row == 7 || col == 0 || col == 7;
    }

    private static void reportWaterSamples(CommandSourceStack source, RiverRegionKey key, RegionDensityProvider provider) {
        int regionSize = RiverRegionKey.getRegionSize();
        double step = regionSize / 8.0;
        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        source.sendSuccess(() -> Component.literal(String.format("  Thresholds: ocean=%.3f, lake=%.3f", oceanThreshold, lakeThreshold))
            .withStyle(ChatFormatting.GRAY), false);

        List<String> waterCells = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int sampleX = key.worldX() + (int) ((i + 0.5) * step);
                int sampleZ = key.worldZ() + (int) ((j + 0.5) * step);

                double continents = provider.getContinents(sampleX, sampleZ);
                double depth = provider.getDepth(sampleX, sampleZ);

                boolean isOcean = continents < oceanThreshold;
                boolean isLake = !isOcean && depth < lakeThreshold;

                if (isOcean || isLake) {
                    String type = isOcean ? "OCEAN" : "LAKE";
                    waterCells.add(String.format("(%d, %d) %s c=%.3f d=%.3f", sampleX, sampleZ, type, continents, depth));
                }
            }
        }

        if (waterCells.isEmpty()) {
            source.sendSuccess(() -> Component.literal("  Water samples: NONE (bug: no water found but classified as SHORE)")
                .withStyle(ChatFormatting.RED), false);
        } else {
            source.sendSuccess(() -> Component.literal(String.format("  Water samples: %d cells", waterCells.size()))
                .withStyle(ChatFormatting.AQUA), false);
            for (String cell : waterCells) {
                String cellCopy = cell;
                source.sendSuccess(() -> Component.literal("    " + cellCopy)
                    .withStyle(ChatFormatting.AQUA), false);
            }
        }
    }
}
