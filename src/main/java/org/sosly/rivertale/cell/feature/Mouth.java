package org.sosly.rivertale.cell.feature;

import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;
import org.sosly.rivertale.world.WorldSettings;

public class Mouth implements FeatureHandler {
    @Override
    public Shape[][] shape(Cell cell, Watershed watershed, ChunkPos chunk) {
        CellPos cellPos = cell.pos();
        Set<CellPos> upstreamSet = watershed.upstream(cellPos);

        if (upstreamSet.isEmpty()) {
            return null;
        }

        int cellSize = CommonConfig.get().cellSize();
        int center = cellSize / 2;

        Direction entryDir = getEntryDirection(cellPos, upstreamSet);
        if (entryDir == Direction.NONE) {
            return null;
        }

        int[] entryPoint = getEdgePoint(entryDir, cellSize, center);
        int[] exitPoint = new int[]{center, center};

        double entrySegmentLength = Math.sqrt(
            Math.pow(center - entryPoint[0], 2) + Math.pow(center - entryPoint[1], 2)
        );

        int centerY = WorldSettings.get().seaLevel() - 1;

        CellCache cache = CellCache.get();
        CellPos upstreamPos = upstreamSet.iterator().next();
        Cell upstreamCell = cache.getOrCompute(upstreamPos);
        int entryY = (centerY + upstreamCell.y()) / 2;

        int cellMinX = cellPos.getMinBlockX();
        int cellMinZ = cellPos.getMinBlockZ();

        int maxDistance = DEFAULT_WIDTH / 2;

        Shape[][] result = new Shape[16][16];

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunk.getMinBlockX() + localX;
                int worldZ = chunk.getMinBlockZ() + localZ;

                int cellLocalX = worldX - cellMinX;
                int cellLocalZ = worldZ - cellMinZ;

                PathInfo pathInfo = getPathInfo(cellLocalX, cellLocalZ, entryPoint, exitPoint, center, center);
                double distance = pathInfo.distance();

                if (distance > maxDistance) {
                    continue;
                }

                FlowInfo flowInfo = computeFlowInfo(pathInfo, entryY, centerY, centerY, entrySegmentLength);
                int waterY = flowInfo.waterY();
                Integer flowLevel = flowInfo.flowLevel();

                ProfileResult profile = calculateProfile(distance, waterY, waterY);
                if (profile == null || !profile.isRiverbed()) {
                    continue;
                }

                Integer shapeFlowLevel = profile.isRiverbed() ? flowLevel : null;
                result[localX][localZ] = new Shape(
                    profile.surfaceY(),
                    profile.weight(),
                    profile.isRiverbed(),
                    profile.waterY(),
                    shapeFlowLevel,
                    entryDir.opposite()
                );
            }
        }

        return result;
    }

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        if (watershed == null) {
            return false;
        }

        Optional<CellPos> terminus = watershed.terminus(cell.pos());
        if (terminus.isEmpty()) {
            return false;
        }

        return terminus.get().equals(cell.pos());
    }

    @Override
    public void fill() {}
}
