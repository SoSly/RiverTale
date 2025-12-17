package org.sosly.rivertale.poc.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RiverCarveExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RiverCarveExecutor.class);

    private final ServerLevel level;
    private final int entryX;
    private final int entryZ;
    private final int exitX;
    private final int exitZ;
    private final int distanceToOcean;
    private final int width;

    private int blocksRemoved = 0;
    private int blocksPlaced = 0;

    public RiverCarveExecutor(ServerLevel level, int entryX, int entryZ, int exitX, int exitZ,
                               int distanceToOcean, int width) {
        this.level = level;
        this.entryX = entryX;
        this.entryZ = entryZ;
        this.exitX = exitX;
        this.exitZ = exitZ;
        this.distanceToOcean = distanceToOcean;
        this.width = width;
    }

    public void execute() {
        long worldSeed = level.getSeed();
        LOGGER.info("Executing carve with seed {}", worldSeed);

        RiverPathInterpolator interpolator = new RiverPathInterpolator(
            entryX, entryZ, exitX, exitZ, distanceToOcean, width, worldSeed
        );

        List<RiverPathInterpolator.PathPoint> pathPoints = interpolator.generatePath();
        LOGGER.info("Generated {} path points", pathPoints.size());

        if (!pathPoints.isEmpty()) {
            RiverPathInterpolator.PathPoint first = pathPoints.get(0);
            LOGGER.info("First path point: ({}, {}) at Y={}", first.x(), first.z(), first.targetElevation());
        }

        int channelDepth = ChannelCarver.calculateChannelDepth(width);
        LOGGER.info("Channel depth: {}", channelDepth);

        List<BlockPos> blocksToRemove = new ArrayList<>();
        List<BlockPos> blocksToPlace = new ArrayList<>();

        for (RiverPathInterpolator.PathPoint point : pathPoints) {
            int waterSurfaceY = point.targetElevation();

            List<BlockPos> channelBlocks = ChannelCarver.getBlocksToCarve(
                point.x(), point.z(), waterSurfaceY, width, channelDepth
            );
            blocksToRemove.addAll(channelBlocks);

            List<BlockPos> riverbedBlocks = ChannelCarver.getRiverbedBlocks(
                point.x(), point.z(), waterSurfaceY, width, channelDepth
            );
            blocksToPlace.addAll(riverbedBlocks);

            List<BlockPos> valleyBlocks = ValleyCarver.getValleyBlocks(
                point.x(), point.z(), waterSurfaceY, width, level
            );
            blocksToRemove.addAll(valleyBlocks);

            List<BlockPos> embankmentBlocks = EmbankmentBuilder.getEmbankmentBlocks(
                point.x(), point.z(), waterSurfaceY, width, level
            );
            blocksToPlace.addAll(embankmentBlocks);
        }

        LOGGER.info("Collected {} blocks to remove, {} blocks to place", blocksToRemove.size(), blocksToPlace.size());

        for (BlockPos pos : blocksToPlace) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
        blocksPlaced = blocksToPlace.size();
        LOGGER.info("Placed {} blocks", blocksPlaced);

        for (BlockPos pos : blocksToRemove) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        blocksRemoved = blocksToRemove.size();
        LOGGER.info("Removed {} blocks", blocksRemoved);
    }

    public int getBlocksRemoved() {
        return blocksRemoved;
    }

    public int getBlocksPlaced() {
        return blocksPlaced;
    }
}
