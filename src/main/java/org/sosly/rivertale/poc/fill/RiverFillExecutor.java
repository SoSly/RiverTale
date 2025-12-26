package org.sosly.rivertale.poc.fill;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sosly.rivertale.poc.carve.CardinalDirection;
import org.sosly.rivertale.poc.carve.ChannelCarver;
import org.sosly.rivertale.poc.carve.RiverPathInterpolator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RiverFillExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RiverFillExecutor.class);

    private final ServerLevel level;
    private final int entryX;
    private final int entryZ;
    private final int exitX;
    private final int exitZ;
    private final int entryDist;
    private final int exitDist;
    private final CardinalDirection direction;
    private final int accumulation;

    private int width;
    private int channelDepth;
    private int waterBlocksPlaced = 0;
    private int pathPointsProcessed = 0;
    private int chunksLoaded = 0;
    private long elapsedMs = 0;

    public RiverFillExecutor(ServerLevel level,
                             int entryX, int entryZ, int entryDist,
                             CardinalDirection direction,
                             int exitX, int exitZ, int exitDist,
                             int accumulation) {
        this.level = level;
        this.entryX = entryX;
        this.entryZ = entryZ;
        this.exitX = exitX;
        this.exitZ = exitZ;
        this.entryDist = entryDist;
        this.exitDist = exitDist;
        this.direction = direction;
        this.accumulation = accumulation;

        RiverPathInterpolator sizeCalc = new RiverPathInterpolator(
            entryX, entryZ, exitX, exitZ, entryDist, exitDist, direction, accumulation, level.getSeed()
        );
        this.width = sizeCalc.getWidth();
        this.channelDepth = ChannelCarver.calculateChannelDepth(width, sizeCalc.getSlope());
    }

    public void execute() {
        LOGGER.info("Filling river: ({},{}) → ({},{}) width={} depth={}",
            entryX, entryZ, exitX, exitZ, width, channelDepth);

        RiverPathInterpolator interpolator = new RiverPathInterpolator(
            entryX, entryZ, exitX, exitZ, entryDist, exitDist, direction, accumulation, level.getSeed()
        );

        List<RiverPathInterpolator.PathPoint> pathPoints = interpolator.generatePath();

        Set<ChunkPos> requiredChunks = calculateRequiredChunks(pathPoints);
        chunksLoaded = requiredChunks.size();
        LOGGER.info("Pre-loading {} chunks...", chunksLoaded);
        for (ChunkPos chunk : requiredChunks) {
            level.getChunk(chunk.x, chunk.z);
        }

        long startTime = System.currentTimeMillis();

        Set<BlockPos> waterBlocks = collectWaterBlocks(pathPoints);
        placeWaterBlocks(waterBlocks);

        elapsedMs = System.currentTimeMillis() - startTime;
        double msPerChunk = chunksLoaded > 0 ? (double) elapsedMs / chunksLoaded : 0;
        LOGGER.info("Filler complete: placed {} water blocks across {} path points in {}ms ({}ms/chunk)",
            waterBlocksPlaced, pathPointsProcessed, elapsedMs, String.format("%.1f", msPerChunk));
    }

    private Set<BlockPos> collectWaterBlocks(List<RiverPathInterpolator.PathPoint> pathPoints) {
        Set<BlockPos> waterBlocks = new HashSet<>();
        int halfWidth = width / 2;

        for (RiverPathInterpolator.PathPoint point : pathPoints) {
            pathPointsProcessed++;
            int targetElevation = point.targetElevation();

            for (int dx = -halfWidth; dx <= halfWidth; dx++) {
                for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                    int lateralOffset = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

                    if (lateralOffset > halfWidth) {
                        continue;
                    }

                    int x = point.x() + dx;
                    int z = point.z() + dz;

                    int groundY = findGroundLevel(x, z, targetElevation);

                    if (groundY >= targetElevation) {
                        continue;
                    }

                    for (int y = groundY + 1; y <= targetElevation; y++) {
                        waterBlocks.add(new BlockPos(x, y, z));
                    }
                }
            }
        }

        return waterBlocks;
    }

    private int findGroundLevel(int x, int z, int maxY) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, maxY, z);

        for (int y = maxY; y >= level.getMinBuildHeight(); y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);

            if (!state.isAir() && !state.canBeReplaced()) {
                return y;
            }
        }

        return level.getMinBuildHeight();
    }

    private void placeWaterBlocks(Set<BlockPos> waterBlocks) {
        BlockState waterState = Blocks.WATER.defaultBlockState();

        for (BlockPos pos : waterBlocks) {
            BlockState existingState = level.getBlockState(pos);

            if (existingState.isAir() || existingState.canBeReplaced()) {
                level.setBlock(pos, waterState, 3);
                waterBlocksPlaced++;
            }
        }
    }

    private Set<ChunkPos> calculateRequiredChunks(List<RiverPathInterpolator.PathPoint> pathPoints) {
        Set<ChunkPos> chunks = new HashSet<>();
        int searchRadius = width / 2;

        for (RiverPathInterpolator.PathPoint point : pathPoints) {
            int minChunkX = (point.x() - searchRadius) >> 4;
            int maxChunkX = (point.x() + searchRadius) >> 4;
            int minChunkZ = (point.z() - searchRadius) >> 4;
            int maxChunkZ = (point.z() + searchRadius) >> 4;

            for (int cx = minChunkX; cx <= maxChunkX; cx++) {
                for (int cz = minChunkZ; cz <= maxChunkZ; cz++) {
                    chunks.add(new ChunkPos(cx, cz));
                }
            }
        }

        return chunks;
    }

    public int getWaterBlocksPlaced() {
        return waterBlocksPlaced;
    }

    public int getPathPointsProcessed() {
        return pathPointsProcessed;
    }

    public int getChunksLoaded() {
        return chunksLoaded;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public int getWidth() {
        return width;
    }

    public int getChannelDepth() {
        return channelDepth;
    }
}
