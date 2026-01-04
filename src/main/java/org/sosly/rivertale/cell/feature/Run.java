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
    private static final int FLAT_RADIUS = 2;
    private static final int DEPTH = 5;
    private static final int EMBANKMENT_RADIUS = 120;

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

        Shape[][] result = new Shape[16][16];

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunk.getMinBlockX() + localX;
                int worldZ = chunk.getMinBlockZ() + localZ;

                int cellLocalX = worldX - cellMinX;
                int cellLocalZ = worldZ - cellMinZ;

                PathInfo pathInfo = getPathInfo(cellLocalX, cellLocalZ, entryPoint, exitPoint, center, center);

                double distance = pathInfo.distance();

                // Geometry: flat riverbed -> underwater slope -> embankment
                int slopeWidth = DEPTH - 1;  // 4 blocks (2-block step + 1 + 1)
                double waterEdge = FLAT_RADIUS + slopeWidth;

                if (distance > EMBANKMENT_RADIUS) {
                    continue;
                }

                int waterY = interpolateY(pathInfo, entryY, centerY, exitY);

                int surfaceY;
                double weight;
                Integer fillWaterY = null;
                boolean isRiverbed;

                if (distance <= FLAT_RADIUS) {
                    // Flat riverbed at bottom
                    surfaceY = waterY - DEPTH;
                    fillWaterY = waterY;
                    isRiverbed = true;
                    weight = 1.0;
                } else if (distance <= waterEdge) {
                    // Underwater slope - rises from riverbed to water level
                    int stepsFromFlat = (int) Math.ceil(distance - FLAT_RADIUS);
                    if (stepsFromFlat == 1) {
                        surfaceY = waterY - DEPTH + 2;  // 2-block rise adjacent to flat
                    } else {
                        surfaceY = waterY - DEPTH + 2 + (stepsFromFlat - 1);  // 1-block rise per step
                    }
                    fillWaterY = waterY;
                    isRiverbed = true;
                    weight = 1.0;
                } else {
                    Sample blockSample = SampleCache.get().getOrCompute(worldX, worldZ);
                    if (blockSample.isOcean()) {
                        continue;
                    }

                    double progress = (distance - waterEdge) / (EMBANKMENT_RADIUS - waterEdge);
                    weight = 1.0 - progress;
                    surfaceY = waterY;
                    isRiverbed = false;
                }

                result[localX][localZ] = new Shape(surfaceY, weight, isRiverbed, fillWaterY);
            }
        }

        return result;
    }
}
