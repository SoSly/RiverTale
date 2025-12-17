package org.sosly.rivertale.poc.carve;

import net.minecraft.core.BlockPos;

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

    public static int getDepthAtOffset(int lateralOffset, int halfWidth, int maxDepth) {
        if (lateralOffset >= halfWidth) {
            return 0;
        }

        double normalizedOffset = (double) lateralOffset / halfWidth;
        double depthFactor = 1.0 - (normalizedOffset * normalizedOffset);

        return (int) Math.round(maxDepth * depthFactor);
    }

    public static List<BlockPos> getBlocksToCarve(int centerX, int centerZ, int waterSurfaceY, int width, int depth) {
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

                for (int dy = 0; dy < depthAtPosition; dy++) {
                    blocks.add(new BlockPos(centerX + dx, waterSurfaceY - dy, centerZ + dz));
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
