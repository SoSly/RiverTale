package org.sosly.rivertale.poc.carve;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

public class ValleyCarver {
    public static int getValleyFloorAtOffset(int lateralOffset, int halfWidth, int waterSurfaceY, int valleySlope) {
        if (lateralOffset <= halfWidth) {
            return waterSurfaceY;
        }

        int distanceBeyondChannel = lateralOffset - halfWidth;
        int slopeHeight = distanceBeyondChannel / valleySlope;

        return waterSurfaceY + slopeHeight;
    }

    public static List<BlockPos> getValleyBlocks(int centerX, int centerZ, int waterSurfaceY, int width, ServerLevel level) {
        List<BlockPos> blocks = new ArrayList<>();
        int halfWidth = width / 2;
        int maxSearchRadius = halfWidth + CarveConfig.MAX_SEARCH_EXTENSION;

        for (int dx = -maxSearchRadius; dx <= maxSearchRadius; dx++) {
            for (int dz = -maxSearchRadius; dz <= maxSearchRadius; dz++) {
                int lateralOffset = (int) Math.round(Math.sqrt(dx * dx + dz * dz));

                int valleyFloorY = getValleyFloorAtOffset(lateralOffset, halfWidth, waterSurfaceY, CarveConfig.VALLEY_SLOPE);
                BlockPos columnPos = new BlockPos(centerX + dx, 0, centerZ + dz);
                int terrainHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, columnPos.getX(), columnPos.getZ()) - 1;

                if (terrainHeight <= valleyFloorY) {
                    continue;
                }

                for (int y = valleyFloorY + 1; y <= terrainHeight; y++) {
                    blocks.add(new BlockPos(centerX + dx, y, centerZ + dz));
                }
            }
        }

        return blocks;
    }
}
