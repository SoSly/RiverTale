package org.sosly.rivertale.cell.feature;

import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public class Run extends AbstractCourseHandler {
    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return watershed != null;
    }

    @Override
    public Shape[][] shape(Cell cell, Watershed watershed, ChunkPos chunk) {
        CellPos cellPos = cell.pos();

        CellPos downstreamPos = watershed.downstream(cellPos);
        Set<CellPos> upstreamSet = watershed.upstream(cellPos);

        if (downstreamPos == null && upstreamSet.isEmpty()) {
            return null;
        }

        int cellSize = CommonConfig.get().cellSize();
        int center = cellSize / 2;

        Direction exitDir = getDirection(cellPos, downstreamPos);
        Direction entryDir = getEntryDirection(cellPos, upstreamSet);

        if (entryDir == Direction.NONE) {
            entryDir = exitDir.opposite();
        }
        if (exitDir == Direction.NONE) {
            exitDir = entryDir.opposite();
        }

        int[] entryPoint = getEdgePoint(entryDir, cellSize, center);
        int[] exitPoint = getEdgePoint(exitDir, cellSize, center);

        double entrySegmentLength = Math.sqrt(
            Math.pow(center - entryPoint[0], 2) + Math.pow(center - entryPoint[1], 2)
        );
        double exitSegmentLength = Math.sqrt(
            Math.pow(exitPoint[0] - center, 2) + Math.pow(exitPoint[1] - center, 2)
        );

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

        int maxDistance = maxInfluenceDistance();

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

                Sample blockSample = SampleCache.get().getOrCompute(worldX, worldZ);
                if (blockSample.isOcean()) {
                    continue;
                }

                double segmentLength = pathInfo.isEntrySegment() ? entrySegmentLength : exitSegmentLength;
                FlowInfo flowInfo = computeFlowInfo(pathInfo, entryY, centerY, exitY, segmentLength);
                int waterY = flowInfo.waterY();
                Integer flowLevel = flowInfo.flowLevel();

                int vanillaY = blockSample.estimatedHeight();

                ProfileResult profile = calculateProfile(distance, waterY, vanillaY);
                if (profile == null) {
                    continue;
                }

                if (profile.isRiverbed()) {
                    boolean inCellBounds = isInCellBounds(cellLocalX, cellLocalZ, cellSize);
                    boolean allowedForRiverbed = inCellBounds
                        || isInCornerNeighbor(cellLocalX, cellLocalZ, cellSize, entryDir)
                        || isInCornerNeighbor(cellLocalX, cellLocalZ, cellSize, exitDir);
                    if (!allowedForRiverbed) {
                        continue;
                    }
                }

                Integer shapeFlowLevel = profile.isRiverbed() ? flowLevel : null;
                result[localX][localZ] = new Shape(
                    profile.surfaceY(),
                    profile.weight(),
                    profile.isRiverbed(),
                    profile.waterY(),
                    shapeFlowLevel
                );
            }
        }

        return result;
    }
}
