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
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.worldgen.river.CellFeatureType;
import org.sosly.rivertale.worldgen.river.RegionDensityProvider;
import org.sosly.rivertale.worldgen.river.RegionFeatureType;
import org.sosly.rivertale.worldgen.river.RiverRegion;
import org.sosly.rivertale.worldgen.river.RiverRegionKey;
import org.sosly.rivertale.worldgen.river.RiverRegionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocateCommand {

    private static final int MAX_SEARCH_RADIUS = 10000;
    private static final ExecutorService LOCATE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "RiverTale-Locate");
        thread.setDaemon(true);
        return thread;
    });

    public static void shutdown() {
        LOCATE_EXECUTOR.shutdownNow();
    }

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("locate");

        for (RegionFeatureType type : RegionFeatureType.values()) {
            builder = builder.then(Commands.literal(type.name().toLowerCase())
                .executes(context -> locateRegionType(context, type)));
        }

        for (CellFeatureType type : CellFeatureType.values()) {
            builder = builder.then(Commands.literal(type.name().toLowerCase())
                .executes(context -> locateCellType(context, type)));
        }

        return builder;
    }

    private static int locateRegionType(CommandContext<CommandSourceStack> context, RegionFeatureType target) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = BlockPos.containing(source.getPosition());

        RandomState randomState = level.getChunkSource().randomState();
        RegionDensityProvider provider = new RegionDensityProvider(randomState);

        int playerX = pos.getX();
        int playerY = pos.getY();
        int playerZ = pos.getZ();
        String featureName = target.name().toLowerCase();

        source.sendSuccess(() -> Component.literal(String.format("Searching for %s...", featureName))
            .withStyle(ChatFormatting.GRAY), false);

        LOCATE_EXECUTOR.submit(() -> {
            BlockPos found = searchForRegionType(playerX, playerY, playerZ, provider, randomState, target);
            server.execute(() -> {
                if (found != null) {
                    sendSuccessMessage(source, found, playerX, playerZ, featureName);
                } else {
                    source.sendFailure(Component.literal(String.format(
                        "Could not find a %s region within %d blocks.",
                        featureName, MAX_SEARCH_RADIUS)));
                }
            });
        });

        return 1;
    }

    private static int locateCellType(CommandContext<CommandSourceStack> context, CellFeatureType target) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos pos = BlockPos.containing(source.getPosition());

        RandomState randomState = level.getChunkSource().randomState();
        RegionDensityProvider provider = new RegionDensityProvider(randomState);

        int playerX = pos.getX();
        int playerY = pos.getY();
        int playerZ = pos.getZ();
        String featureName = target.name().toLowerCase();

        source.sendSuccess(() -> Component.literal(String.format("Searching for %s...", featureName))
            .withStyle(ChatFormatting.GRAY), false);

        LOCATE_EXECUTOR.submit(() -> {
            BlockPos found = searchForCellType(playerX, playerY, playerZ, provider, randomState, target);
            server.execute(() -> {
                if (found != null) {
                    sendSuccessMessage(source, found, playerX, playerZ, featureName);
                } else {
                    source.sendFailure(Component.literal(String.format(
                        "Could not find a %s cell within %d blocks.",
                        featureName, MAX_SEARCH_RADIUS)));
                }
            });
        });

        return 1;
    }

    private static BlockPos searchForRegionType(int playerX, int playerY, int playerZ, RegionDensityProvider provider, RandomState randomState, RegionFeatureType target) {
        int regionSize = RiverRegionKey.getRegionSize();
        int playerRegionX = Math.floorDiv(playerX, regionSize);
        int playerRegionZ = Math.floorDiv(playerZ, regionSize);
        int maxRegions = MAX_SEARCH_RADIUS / regionSize;

        for (int ring = 0; ring <= maxRegions; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                BlockPos found = checkRegionType(playerRegionX + dx, playerRegionZ - ring, playerY, provider, randomState, target);
                if (found != null) {
                    return found;
                }
                if (ring > 0) {
                    found = checkRegionType(playerRegionX + dx, playerRegionZ + ring, playerY, provider, randomState, target);
                    if (found != null) {
                        return found;
                    }
                }
            }

            for (int dz = -ring + 1; dz < ring; dz++) {
                BlockPos found = checkRegionType(playerRegionX - ring, playerRegionZ + dz, playerY, provider, randomState, target);
                if (found != null) {
                    return found;
                }
                found = checkRegionType(playerRegionX + ring, playerRegionZ + dz, playerY, provider, randomState, target);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private static BlockPos searchForCellType(int playerX, int playerY, int playerZ, RegionDensityProvider provider, RandomState randomState, CellFeatureType target) {
        int regionSize = RiverRegionKey.getRegionSize();
        int playerRegionX = Math.floorDiv(playerX, regionSize);
        int playerRegionZ = Math.floorDiv(playerZ, regionSize);
        int maxRegions = MAX_SEARCH_RADIUS / regionSize;

        for (int ring = 0; ring <= maxRegions; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                BlockPos found = checkCellType(playerRegionX + dx, playerRegionZ - ring, playerY, provider, randomState, target);
                if (found != null) {
                    return found;
                }
                if (ring > 0) {
                    found = checkCellType(playerRegionX + dx, playerRegionZ + ring, playerY, provider, randomState, target);
                    if (found != null) {
                        return found;
                    }
                }
            }

            for (int dz = -ring + 1; dz < ring; dz++) {
                BlockPos found = checkCellType(playerRegionX - ring, playerRegionZ + dz, playerY, provider, randomState, target);
                if (found != null) {
                    return found;
                }
                found = checkCellType(playerRegionX + ring, playerRegionZ + dz, playerY, provider, randomState, target);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    private static BlockPos checkRegionType(int regionX, int regionZ, int playerY, RegionDensityProvider provider, RandomState randomState, RegionFeatureType target) {
        RiverRegionKey key = new RiverRegionKey(regionX, regionZ);
        RiverRegion region = RiverRegionManager.getOrCreate(key, provider, randomState);
        RegionFeatureType type = RiverRegionManager.getRegionFeatureType(region, provider, randomState);

        if (type == target) {
            return new BlockPos(key.centerX(), playerY, key.centerZ());
        }

        return null;
    }

    private static BlockPos checkCellType(int regionX, int regionZ, int playerY, RegionDensityProvider provider, RandomState randomState, CellFeatureType target) {
        RiverRegionKey key = new RiverRegionKey(regionX, regionZ);
        RiverRegion region = RiverRegionManager.getOrCreate(key, provider, randomState);

        if (!region.isParticipating()) {
            return null;
        }

        RiverRegionManager.ensurePaths(region, provider, randomState);

        int regionSize = RiverRegionKey.getRegionSize();
        int cellSpacing = regionSize / 8;

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                CellFeatureType cellType = RiverRegionManager.getCellFeatureType(region, row, col);
                if (cellType != null && cellType == target) {
                    int cellX = key.worldX() + (col * cellSpacing) + (cellSpacing / 2);
                    int cellZ = key.worldZ() + (row * cellSpacing) + (cellSpacing / 2);
                    return new BlockPos(cellX, playerY, cellZ);
                }
            }
        }

        return null;
    }

    private static void sendSuccessMessage(CommandSourceStack source, BlockPos target, int playerX, int playerZ, String featureName) {
        int distance = (int) Math.sqrt(
            Math.pow(target.getX() - playerX, 2) + Math.pow(target.getZ() - playerZ, 2)
        );

        Component coords = Component.literal(String.format("[%d, %d, %d]", target.getX(), target.getY(), target.getZ()))
            .withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                    String.format("/tp @s %d %d %d", target.getX(), target.getY(), target.getZ())))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                    Component.literal("Click to teleport")))
            );

        Component message = Component.literal(String.format("The nearest %s is at ", featureName))
            .append(coords)
            .append(Component.literal(String.format(" (%d blocks away)", distance)));

        source.sendSuccess(() -> message, false);
    }
}
