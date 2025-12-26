package org.sosly.rivertale.poc.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RiverCarveExecutor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RiverCarveExecutor.class);

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
    private double slope;
    private int channelDepth;
    private int blocksRemoved = 0;
    private int blocksPlaced = 0;
    private int chunksLoaded = 0;
    private long elapsedMs = 0;

    public RiverCarveExecutor(ServerLevel level,
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
        this.slope = sizeCalc.getSlope();
        this.channelDepth = ChannelCarver.calculateChannelDepth(width, slope);
    }

    public void execute() {
        LOGGER.info("Carving river: ({},{}) → ({},{}) width={} depth={}",
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

        List<BlockPos> blocksToRemove = new ArrayList<>();
        List<BlockPos> blocksToPlace = new ArrayList<>();

        for (RiverPathInterpolator.PathPoint point : pathPoints) {
            int waterSurfaceY = point.targetElevation();

            List<BlockPos> channelBlocks = ChannelCarver.getBlocksToCarve(
                point.x(), point.z(), waterSurfaceY, width, channelDepth, level
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

            List<BlockPos> embankmentClearBlocks = EmbankmentBuilder.getBlocksToClear(
                point.x(), point.z(), waterSurfaceY, width, level
            );
            blocksToRemove.addAll(embankmentClearBlocks);
        }

        for (BlockPos pos : blocksToPlace) {
            level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
        }
        blocksPlaced = blocksToPlace.size();

        for (BlockPos pos : blocksToRemove) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
        blocksRemoved = blocksToRemove.size();

        elapsedMs = System.currentTimeMillis() - startTime;
        double msPerChunk = chunksLoaded > 0 ? (double) elapsedMs / chunksLoaded : 0;
        LOGGER.info("Carver complete: placed {} blocks, removed {} blocks in {}ms ({}ms/chunk)",
            blocksPlaced, blocksRemoved, elapsedMs, String.format("%.1f", msPerChunk));
    }

    private Set<ChunkPos> calculateRequiredChunks(List<RiverPathInterpolator.PathPoint> pathPoints) {
        Set<ChunkPos> chunks = new HashSet<>();
        int searchRadius = width / 2 + CarveConfig.MAX_SEARCH_EXTENSION;

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

    public int getBlocksRemoved() {
        return blocksRemoved;
    }

    public int getBlocksPlaced() {
        return blocksPlaced;
    }

    public int getWidth() {
        return width;
    }

    public double getSlope() {
        return slope;
    }

    public int getChannelDepth() {
        return channelDepth;
    }

    public int getChunksLoaded() {
        return chunksLoaded;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }
}
