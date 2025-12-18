package org.sosly.rivertale.poc.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public class ChannelCarver {
    public static int calculateChannelDepth(int width) {
        if (width <= CarveConfig.MIN_WIDTH) {
            return CarveConfig.MIN_CHANNEL_DEPTH;
        }
        if (width >= CarveConfig.MAX_WIDTH) {
            return CarveConfig.MAX_CHANNEL_DEPTH;
        }

        double widthRange = CarveConfig.MAX_WIDTH - CarveConfig.MIN_WIDTH;
        double depthRange = CarveConfig.MAX_CHANNEL_DEPTH - CarveConfig.MIN_CHANNEL_DEPTH;
        double normalizedWidth = (width - CarveConfig.MIN_WIDTH) / widthRange;

        return CarveConfig.MIN_CHANNEL_DEPTH + (int) Math.round(normalizedWidth * depthRange);
    }

    public static int calculateChannelDepth(int width, double slope) {
        int baseDepth = calculateChannelDepth(width);
        double modifier = 1.0 / (1.0 + slope * CarveConfig.SLOPE_DEPTH_FACTOR);
        int depth = (int) Math.round(baseDepth * modifier);
        return Math.max(CarveConfig.MIN_CHANNEL_DEPTH, Math.min(CarveConfig.MAX_CHANNEL_DEPTH, depth));
    }

    public static int getDepthAtOffset(int lateralOffset, int halfWidth, int maxDepth) {
        if (lateralOffset >= halfWidth) {
            return 0;
        }

        double normalizedOffset = (double) lateralOffset / halfWidth;
        double depthFactor = 1.0 - (normalizedOffset * normalizedOffset);

        return (int) Math.round(maxDepth * depthFactor);
    }

    public static List<BlockPos> getBlocksToCarve(int centerX, int centerZ, int waterSurfaceY, int width, int depth, ServerLevel level) {
        List<BlockPos> blocks = new ArrayList<>();
        int halfWidth = width / 2;
        int worldTop = level.getMaxBuildHeight() - 1;

        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                int lateralOffset = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

                if (lateralOffset > halfWidth) {
                    continue;
                }

                int depthAtPosition = getDepthAtOffset(lateralOffset, halfWidth, depth);

                if (depthAtPosition == 0) {
                    continue;
                }

                int x = centerX + dx;
                int z = centerZ + dz;
                int carveBottom = waterSurfaceY - depthAtPosition + 1;

                for (int y = carveBottom; y <= worldTop; y++) {
                    blocks.add(new BlockPos(x, y, z));
                }
            }
        }

        return blocks;
    }

    public static List<BlockPos> getRiverbedBlocks(int centerX, int centerZ, int waterSurfaceY, int width, int depth) {
        List<BlockPos> blocks = new ArrayList<>();
        int halfWidth = width / 2;

        for (int dx = -halfWidth; dx <= halfWidth; dx++) {
            for (int dz = -halfWidth; dz <= halfWidth; dz++) {
                int lateralOffset = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

                if (lateralOffset > halfWidth) {
                    continue;
                }

                int depthAtPosition = getDepthAtOffset(lateralOffset, halfWidth, depth);

                if (depthAtPosition == 0) {
                    continue;
                }

                int riverbedY = waterSurfaceY - depthAtPosition;
                blocks.add(new BlockPos(centerX + dx, riverbedY, centerZ + dz));
            }
        }

        return blocks;
    }
}
