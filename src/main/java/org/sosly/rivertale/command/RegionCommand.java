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
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.terrain.RegionDensityProvider;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.path.Manager;

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

                RegionPos regionPos = RegionPos.at(pos.getX(), pos.getZ());
                Region region = Manager.createRegionFor(regionPos, provider, randomState);

                source.sendSuccess(() -> Component.literal("-----").withStyle(ChatFormatting.GRAY), false);
                source.sendSuccess(() -> Component.literal(String.format("Region (%d, %d)", regionPos.x(), regionPos.z()))
                    .withStyle(ChatFormatting.YELLOW), false);

                RegionType featureRegionType = Manager.getRegionFeatureType(region, provider, randomState);

                if (!region.isParticipating()) {
                    source.sendSuccess(() -> Component.literal("  Participating: false")
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Region CellType: %s", featureRegionType))
                        .withStyle(ChatFormatting.WHITE), false);
                    return 1;
                }

                boolean isTerminus = featureRegionType == RegionType.BODY || featureRegionType == RegionType.SHORE;

                if (isTerminus) {
                    source.sendSuccess(() -> Component.literal(String.format("  Center: (%,d, %,d)", regionPos.centerX(), regionPos.centerZ()))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Density: %.3f", region.getDensity()))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Region CellType: %s", featureRegionType))
                        .withStyle(ChatFormatting.WHITE), false);

                    int distance = Manager.getDistanceToTerminus(region, provider, randomState);
                    source.sendSuccess(() -> Component.literal(String.format("  Distance to terminus: %d", distance))
                        .withStyle(ChatFormatting.WHITE), false);

                    if (featureRegionType == RegionType.SHORE) {
                        int upstreamCount = Manager.getUpstreamCount(regionPos, provider, randomState);
                        source.sendSuccess(() -> Component.literal(String.format("  Upstream count: %d", upstreamCount))
                            .withStyle(ChatFormatting.WHITE), false);

                        reportWaterSamples(source, regionPos, provider);
                    }

                    Map<Direction, List<CellPos>> terminusPaths = Manager.computePaths(region, provider, randomState);
                    if (!terminusPaths.isEmpty()) {
                        for (Map.Entry<Direction, List<CellPos>> entry : terminusPaths.entrySet()) {
                            Direction inputDirection = entry.getKey();
                            List<CellPos> path = entry.getValue();
                            String pathStr = path.stream()
                                .map(cell -> String.format("(%d,%d)", cell.row(), cell.col()))
                                .collect(Collectors.joining(" -> "));
                            source.sendSuccess(() -> Component.literal(String.format("  Path from %s: %s", inputDirection, pathStr))
                                .withStyle(ChatFormatting.WHITE), false);
                        }
                    }
                    return 1;
                }

                source.sendSuccess(() -> Component.literal(String.format("  Center: (%,d, %,d)", regionPos.centerX(), regionPos.centerZ()))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Density: %.3f", region.getDensity()))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal(String.format("  Region CellType: %s", featureRegionType))
                    .withStyle(ChatFormatting.WHITE), false);
                source.sendSuccess(() -> Component.literal("  Participating: true")
                    .withStyle(ChatFormatting.WHITE), false);

                Direction primaryOutput = region.getPrimaryOutput();
                source.sendSuccess(() -> Component.literal(String.format("  Output: %s", primaryOutput))
                    .withStyle(ChatFormatting.WHITE), false);

                Set<Direction> secondaryOutputs = region.getSecondaryOutputs();
                if (!secondaryOutputs.isEmpty()) {
                    String secondaryStr = secondaryOutputs.stream()
                        .map(Direction::toString)
                        .sorted()
                        .collect(Collectors.joining(", "));
                    source.sendSuccess(() -> Component.literal(String.format("  Secondary outputs: %s", secondaryStr))
                        .withStyle(ChatFormatting.WHITE), false);
                }

                int distance = Manager.getDistanceToTerminus(region, provider, randomState);
                source.sendSuccess(() -> Component.literal(String.format("  Distance to terminus: %d", distance))
                    .withStyle(ChatFormatting.WHITE), false);

                int upstreamCount = Manager.getUpstreamCount(regionPos, provider, randomState);
                source.sendSuccess(() -> Component.literal(String.format("  Upstream count: %d", upstreamCount))
                    .withStyle(ChatFormatting.WHITE), false);

                Map<Direction, List<CellPos>> paths = Manager.computePaths(region, provider, randomState);
                if (!paths.isEmpty()) {
                    for (Map.Entry<Direction, List<CellPos>> entry : paths.entrySet()) {
                        Direction inputDirection = entry.getKey();
                        List<CellPos> path = entry.getValue();
                        String pathStr = path.stream()
                            .map(cell -> String.format("(%d,%d)", cell.row(), cell.col()))
                            .collect(Collectors.joining(" -> "));
                        String label = formatPathLabel(inputDirection, primaryOutput, region, paths);
                        source.sendSuccess(() -> Component.literal(String.format("  %s: %s", label, pathStr))
                            .withStyle(ChatFormatting.WHITE), false);
                    }
                }

                return 1;
            });
    }

    private static String formatPathLabel(Direction direction, Direction primaryOutput, Region region, Map<Direction, List<CellPos>> paths) {
        if (direction == primaryOutput && !isEdgeDirection(direction, paths)) {
            return String.format("Source to %s", primaryOutput);
        }

        if (region.getSecondaryOutputs().contains(direction)) {
            return String.format("Secondary source to %s", direction);
        }

        return String.format("Path from %s to %s", direction, primaryOutput);
    }

    private static boolean isEdgeDirection(Direction direction, Map<Direction, List<CellPos>> paths) {
        List<CellPos> path = paths.get(direction);
        if (path == null || path.isEmpty()) {
            return false;
        }
        CellPos start = path.get(0);
        int row = start.row();
        int col = start.col();
        return row == 0 || row == 7 || col == 0 || col == 7;
    }

    private static void reportWaterSamples(CommandSourceStack source, RegionPos regionPos, RegionDensityProvider provider) {
        int regionSize = RegionPos.getRegionSize();
        double step = regionSize / 8.0;
        double oceanThreshold = RiverConfig.OCEAN_THRESHOLD.get();
        double lakeThreshold = RiverConfig.LAKE_THRESHOLD.get();

        source.sendSuccess(() -> Component.literal(String.format("  Thresholds: ocean=%.3f, lake=%.3f", oceanThreshold, lakeThreshold))
            .withStyle(ChatFormatting.GRAY), false);

        List<String> waterCells = new ArrayList<>();

        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                int sampleX = regionPos.worldX() + (int) ((i + 0.5) * step);
                int sampleZ = regionPos.worldZ() + (int) ((j + 0.5) * step);

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
