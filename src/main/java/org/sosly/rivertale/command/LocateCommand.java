package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.worldgen.river.CellClassification;
import org.sosly.rivertale.worldgen.river.CellDensityProvider;
import org.sosly.rivertale.worldgen.river.PathDirection;
import org.sosly.rivertale.worldgen.river.RiverCell;
import org.sosly.rivertale.worldgen.river.RiverCellKey;
import org.sosly.rivertale.worldgen.river.RiverCellManager;

public class LocateCommand {

    private static final int MAX_SEARCH_RADIUS = 10000;

    private enum FeatureType {
        BASIN,
        SOURCE,
        CONFLUENCE
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("locate")
            .then(Commands.literal("ocean").executes(context -> locateOcean(context)))
            .then(Commands.literal("coastal").executes(context -> locateCoastal(context)))
            .then(Commands.literal("basin").executes(context -> locateBasin(context)))
            .then(Commands.literal("source").executes(context -> locateSource(context)))
            .then(Commands.literal("confluence").executes(context -> locateConfluence(context)));
    }

    private static int locateOcean(CommandContext<CommandSourceStack> context) {
        return locate(context, CellClassification.OCEAN);
    }

    private static int locateCoastal(CommandContext<CommandSourceStack> context) {
        return locate(context, CellClassification.COASTAL);
    }

    private static int locateBasin(CommandContext<CommandSourceStack> context) {
        return locateFlowFeature(context, FeatureType.BASIN);
    }

    private static int locateSource(CommandContext<CommandSourceStack> context) {
        return locateFlowFeature(context, FeatureType.SOURCE);
    }

    private static int locateConfluence(CommandContext<CommandSourceStack> context) {
        return locateFlowFeature(context, FeatureType.CONFLUENCE);
    }

    private static int locate(CommandContext<CommandSourceStack> context, CellClassification target) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());

        RandomState randomState = level.getChunkSource().randomState();
        CellDensityProvider provider = new CellDensityProvider(randomState);

        int playerX = pos.getX();
        int playerZ = pos.getZ();
        int cellSize = RiverCellKey.getCellSize();
        int playerCellX = Math.floorDiv(playerX, cellSize);
        int playerCellZ = Math.floorDiv(playerZ, cellSize);

        int maxCells = MAX_SEARCH_RADIUS / cellSize;

        for (int ring = 0; ring <= maxCells; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                BlockPos found = checkCell(playerCellX + dx, playerCellZ - ring, cellSize, provider, target);
                if (found != null) {
                    sendSuccessMessage(source, found, playerX, playerZ, target);
                    return 1;
                }
                if (ring > 0) {
                    found = checkCell(playerCellX + dx, playerCellZ + ring, cellSize, provider, target);
                    if (found != null) {
                        sendSuccessMessage(source, found, playerX, playerZ, target);
                        return 1;
                    }
                }
            }

            for (int dz = -ring + 1; dz < ring; dz++) {
                BlockPos found = checkCell(playerCellX - ring, playerCellZ + dz, cellSize, provider, target);
                if (found != null) {
                    sendSuccessMessage(source, found, playerX, playerZ, target);
                    return 1;
                }
                found = checkCell(playerCellX + ring, playerCellZ + dz, cellSize, provider, target);
                if (found != null) {
                    sendSuccessMessage(source, found, playerX, playerZ, target);
                    return 1;
                }
            }
        }

        String featureName = target == CellClassification.OCEAN ? "ocean" : "coastal";
        source.sendFailure(Component.literal(String.format(
            "Could not find an %s within %d blocks.",
            featureName, MAX_SEARCH_RADIUS)));
        return 0;
    }

    private static int locateFlowFeature(CommandContext<CommandSourceStack> context, FeatureType featureType) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        BlockPos pos = BlockPos.containing(source.getPosition());

        RandomState randomState = level.getChunkSource().randomState();
        CellDensityProvider provider = new CellDensityProvider(randomState);

        int playerX = pos.getX();
        int playerZ = pos.getZ();
        int cellSize = RiverCellKey.getCellSize();
        int playerCellX = Math.floorDiv(playerX, cellSize);
        int playerCellZ = Math.floorDiv(playerZ, cellSize);

        int maxCells = MAX_SEARCH_RADIUS / cellSize;

        for (int ring = 0; ring <= maxCells; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                BlockPos found = checkFlowFeatureCell(playerCellX + dx, playerCellZ - ring, cellSize, provider, randomState, featureType);
                if (found != null) {
                    sendFlowFeatureMessage(source, found, playerX, playerZ, featureType);
                    return 1;
                }
                if (ring > 0) {
                    found = checkFlowFeatureCell(playerCellX + dx, playerCellZ + ring, cellSize, provider, randomState, featureType);
                    if (found != null) {
                        sendFlowFeatureMessage(source, found, playerX, playerZ, featureType);
                        return 1;
                    }
                }
            }

            for (int dz = -ring + 1; dz < ring; dz++) {
                BlockPos found = checkFlowFeatureCell(playerCellX - ring, playerCellZ + dz, cellSize, provider, randomState, featureType);
                if (found != null) {
                    sendFlowFeatureMessage(source, found, playerX, playerZ, featureType);
                    return 1;
                }
                found = checkFlowFeatureCell(playerCellX + ring, playerCellZ + dz, cellSize, provider, randomState, featureType);
                if (found != null) {
                    sendFlowFeatureMessage(source, found, playerX, playerZ, featureType);
                    return 1;
                }
            }
        }

        String featureName = featureType.name().toLowerCase();
        source.sendFailure(Component.literal(String.format(
            "Could not find a %s within %d blocks.",
            featureName, MAX_SEARCH_RADIUS)));
        return 0;
    }

    private static BlockPos checkCell(int cellX, int cellZ, int cellSize, CellDensityProvider provider, CellClassification target) {
        RiverCellKey key = new RiverCellKey(cellX, cellZ);
        CellClassification classification = classify(key, cellSize, provider);

        if (classification == target) {
            return new BlockPos(key.centerX(), 63, key.centerZ());
        }

        return null;
    }

    private static BlockPos checkFlowFeatureCell(int cellX, int cellZ, int cellSize, CellDensityProvider provider, RandomState randomState, FeatureType featureType) {
        RiverCellKey key = new RiverCellKey(cellX, cellZ);
        RiverCell cell = RiverCellManager.getOrCreate(key, provider, randomState);

        if (!cell.isParticipating()) {
            return null;
        }

        if (cell.getClassification() != CellClassification.LAND) {
            return null;
        }

        boolean matches = switch (featureType) {
            case BASIN -> cell.isBasin();
            case SOURCE -> countInputs(cell, provider, randomState) == 0;
            case CONFLUENCE -> countInputs(cell, provider, randomState) >= 2;
        };

        if (matches) {
            return new BlockPos(key.centerX(), 63, key.centerZ());
        }

        return null;
    }

    private static int countInputs(RiverCell cell, CellDensityProvider provider, RandomState randomState) {
        int inputs = 0;
        RiverCellKey key = cell.getKey();
        PathDirection[] cardinals = {PathDirection.NORTH, PathDirection.SOUTH, PathDirection.EAST, PathDirection.WEST};

        for (PathDirection direction : cardinals) {
            RiverCellKey neighborKey = direction.neighbor(key);
            RiverCell neighbor = RiverCellManager.getOrCreate(neighborKey, provider, randomState);

            if (!neighbor.isParticipating()) {
                continue;
            }

            RiverCellKey outputTarget = neighbor.getPrimaryOutput().neighbor(neighborKey);
            if (outputTarget != null && outputTarget.equals(key)) {
                inputs++;
            }
        }

        return inputs;
    }

    private static void sendSuccessMessage(CommandSourceStack source, BlockPos target, int playerX, int playerZ, CellClassification classification) {
        int distance = (int) Math.sqrt(
            Math.pow(target.getX() - playerX, 2) + Math.pow(target.getZ() - playerZ, 2)
        );

        String featureName = classification == CellClassification.OCEAN ? "ocean" : "coastal";

        Component coords = Component.literal(String.format("[%d, 63, %d]", target.getX(), target.getZ()))
            .withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                    String.format("/tp @s %d 63 %d", target.getX(), target.getZ())))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("Click to teleport")))
            );

        Component message = Component.literal(String.format("The nearest %s is at ", featureName))
            .append(coords)
            .append(Component.literal(String.format(" (%d blocks away)", distance)));

        source.sendSuccess(() -> message, false);
    }

    private static void sendFlowFeatureMessage(CommandSourceStack source, BlockPos target, int playerX, int playerZ, FeatureType featureType) {
        int distance = (int) Math.sqrt(
            Math.pow(target.getX() - playerX, 2) + Math.pow(target.getZ() - playerZ, 2)
        );

        String featureName = featureType.name().toLowerCase();

        Component coords = Component.literal(String.format("[%d, 63, %d]", target.getX(), target.getZ()))
            .withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                    String.format("/tp @s %d 63 %d", target.getX(), target.getZ())))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("Click to teleport")))
            );

        Component message = Component.literal(String.format("The nearest %s is at ", featureName))
            .append(coords)
            .append(Component.literal(String.format(" (%d blocks away)", distance)));

        source.sendSuccess(() -> message, false);
    }

    private static CellClassification classify(RiverCellKey key, int cellSize, CellDensityProvider provider) {
        boolean hasLand = false;
        boolean hasOcean = false;
        double step = cellSize / 8.0;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int sampleX = key.worldX() + (int) (col * step);
                int sampleZ = key.worldZ() + (int) (row * step);
                if (provider.isOcean(sampleX, sampleZ)) {
                    hasOcean = true;
                } else {
                    hasLand = true;
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
