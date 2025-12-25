package org.sosly.rivertale.poc.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

public class EmbankmentBuilder {
    public static int getEmbankmentTopAtOffset(int lateralOffset, int halfWidth, int waterSurfaceY) {
        if (lateralOffset <= halfWidth) {
            return waterSurfaceY;
        }

        int distanceBeyondChannel = lateralOffset - halfWidth;
        int slopeHeight = distanceBeyondChannel / CarveConfig.BANK_SLOPE;
        return waterSurfaceY - slopeHeight;
    }

    public static List<BlockPos> getEmbankmentBlocks(int centerX, int centerZ, int waterSurfaceY, int width, ServerLevel level) {
        List<BlockPos> blocks = new ArrayList<>();
        int halfWidth = width / 2;
        int maxSearchRadius = halfWidth + CarveConfig.MAX_SEARCH_EXTENSION;

        for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
            for (int dz = -maxSearchRadius; dz <= maxSearchRadius; dz++) {
                int lateralOffset = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

                if (lateralOffset <= halfWidth) {
                    continue;
                }

                int embankmentTopY = getEmbankmentTopAtOffset(lateralOffset, halfWidth, waterSurfaceY);
                BlockPos columnPos = new BlockPos(centerX + dx, 0, centerZ + dz);
                int terrainHeight = level.getHeight(CarveConfig.TERRAIN_HEIGHTMAP, columnPos.getX(), columnPos.getZ()) - 1;

                if (terrainHeight >= embankmentTopY) {
                    continue;
                }

                for (int y = terrainHeight + 1; y <= embankmentTopY; y++) {
                    blocks.add(new BlockPos(centerX + dx, y, centerZ + dz));
                }
            }
        }

        return blocks;
    }

    public static List<BlockPos> getBlocksToClear(int centerX, int centerZ, int waterSurfaceY, int width, ServerLevel level) {
        List<BlockPos> blocks = new ArrayList<>();
        int halfWidth = width / 2;
        int maxSearchRadius = halfWidth + CarveConfig.MAX_SEARCH_EXTENSION;

        for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
            for (int dz = -maxSearchRadius; dz <= maxSearchRadius; dz++) {
                int lateralOffset = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

                if (lateralOffset <= halfWidth) {
                    continue;
                }

                int embankmentTopY = getEmbankmentTopAtOffset(lateralOffset, halfWidth, waterSurfaceY);
                BlockPos columnPos = new BlockPos(centerX + dx, 0, centerZ + dz);
                int terrainHeight = level.getHeight(CarveConfig.TERRAIN_HEIGHTMAP, columnPos.getX(), columnPos.getZ()) - 1;

                if (terrainHeight >= embankmentTopY) {
                    continue;
                }

                int motionBlockingHeight = level.getHeight(
                    Heightmap.Types.MOTION_BLOCKING, columnPos.getX(), columnPos.getZ()) - 1;

                if (motionBlockingHeight <= embankmentTopY) {
                    continue;
                }

                for (int y = embankmentTopY + 1; y <= motionBlockingHeight; y++) {
                    blocks.add(new BlockPos(centerX + dx, y, centerZ + dz));
                }
            }
        }

        return blocks;
    }
}
