package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.FeatureHandler;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.river.WatershedCache;
import org.sosly.rivertale.terrain.Shape;
import org.sosly.rivertale.RiverTale;

public class DebugCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("debug")
                .then(Commands.literal("river").executes(DebugCommand::river));
    }

    private static int river(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        try {
            BlockPos pos = player.blockPosition();
            sendRiverDebugInfo(player, pos);
        } catch (Exception e) {
            RiverTale.LOGGER.error("Error executing debug river command", e);
            return 0;
        }

        return 1;
    }

    private static void sendRiverDebugInfo(ServerPlayer player, BlockPos pos) {
        CellPos cellPos = new CellPos(pos);
        Cell cell = CellCache.get().getOrCompute(cellPos);

        Sample blockSample = SampleCache.get().getOrCompute(pos.getX(), pos.getZ());
        int vanillaY = blockSample.estimatedHeight();
        int waterY = cell.y();
        boolean needsBank = vanillaY <= waterY;

        player.sendSystemMessage(Component.literal("=== River Debug at " + pos.getX() + ", " + pos.getZ() + " ==="));
        player.sendSystemMessage(Component.literal("Cell: " + cellPos + " (" + cell.feature().name() + ")"));
        player.sendSystemMessage(Component.literal("waterY: " + waterY + ", vanillaY: " + vanillaY + ", needsBank: " + needsBank));

        Watershed watershed = WatershedCache.get().getWatershed(cellPos);
        if (watershed == null) {
            player.sendSystemMessage(Component.literal("(no watershed data)"));
            return;
        }

        CellPos downstreamPos = watershed.downstream(cellPos);
        Set<CellPos> upstreamSet = watershed.upstream(cellPos);

        if (downstreamPos == null && upstreamSet.isEmpty()) {
            player.sendSystemMessage(Component.literal("(no river path)"));
            return;
        }

        int cellSize = CommonConfig.get().cellSize();
        int center = cellSize / 2;

        PathHelper helper = new PathHelper();
        Direction exitDir = helper.getDirection(cellPos, downstreamPos);
        Direction entryDir = helper.getEntryDirection(cellPos, upstreamSet);

        if (entryDir == Direction.NONE) {
            entryDir = exitDir.opposite();
        }
        if (exitDir == Direction.NONE) {
            exitDir = entryDir.opposite();
        }

        int[] entryPoint = helper.getEdgePoint(entryDir, cellSize, center);
        int[] exitPoint = helper.getEdgePoint(exitDir, cellSize, center);

        CellCache cache = CellCache.get();

        int entryY;
        if (!upstreamSet.isEmpty()) {
            CellPos upstreamPos = upstreamSet.iterator().next();
            Cell upstreamCell = cache.getOrCompute(upstreamPos);
            entryY = (cell.y() + upstreamCell.y()) / 2;
        } else {
            entryY = cell.y();
        }

        int centerY = cell.y();

        int exitY;
        if (downstreamPos != null) {
            Cell downstreamCell = cache.getOrCompute(downstreamPos);
            exitY = (cell.y() + downstreamCell.y()) / 2;
        } else {
            exitY = cell.y();
        }

        int cellMinX = cellPos.getMinBlockX();
        int cellMinZ = cellPos.getMinBlockZ();
        int cellLocalX = pos.getX() - cellMinX;
        int cellLocalZ = pos.getZ() - cellMinZ;

        FeatureHandler.PathInfo pathInfo = helper.getPathInfo(cellLocalX, cellLocalZ, entryPoint, exitPoint, center, center);
        int interpolatedWaterY = helper.interpolateY(pathInfo, entryY, centerY, exitY);

        FeatureHandler.ProfileResult profile = helper.calculateProfile(pathInfo.distance(), interpolatedWaterY, vanillaY);

        player.sendSystemMessage(Component.literal("entryDir: " + entryDir + ", exitDir: " + exitDir));
        player.sendSystemMessage(Component.literal("entryY: " + entryY + ", centerY: " + centerY + ", exitY: " + exitY));
        player.sendSystemMessage(Component.literal("cellLocal: [" + cellLocalX + ", " + cellLocalZ + "]"));
        player.sendSystemMessage(Component.literal("distance: " + format(pathInfo.distance())));
        player.sendSystemMessage(Component.literal("segment: " + (pathInfo.isEntrySegment() ? "ENTRY" : "EXIT") + ", t: " + format(pathInfo.t())));
        player.sendSystemMessage(Component.literal("interpolatedWaterY: " + interpolatedWaterY));
        if (profile != null) {
            player.sendSystemMessage(Component.literal("profile: surfaceY=" + profile.surfaceY() + ", isRiverbed=" + profile.isRiverbed() + ", weight=" + format(profile.weight())));
        } else {
            player.sendSystemMessage(Component.literal("profile: null (outside influence)"));
        }
    }

    private static String format(double value) {
        return String.format("%.3f", value);
    }

    private static class PathHelper implements FeatureHandler {
        @Override
        public FeatureHandler.ProfileResult calculateProfile(double distance, int waterY, int vanillaY) {
            return FeatureHandler.super.calculateProfile(distance, waterY, vanillaY);
        }

        @Override
        public Shape[][] shape(Cell cell, Watershed watershed, ChunkPos pos) {
            return null;
        }

        @Override
        public boolean classify(Cell cell, Watershed watershed) {
            return false;
        }

        @Override
        public void fill() {}
    }
}
