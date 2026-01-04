package org.sosly.rivertale.cell.feature;

import java.util.ArrayList;
import java.util.List;
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

public class Confluence implements FeatureHandler {
    private static final int FLAT_RADIUS = 3;
    private static final int DEPTH = 5;
    private static final int EMBANKMENT_RADIUS = 120;

    private record EntryInfo(int[] point, int y) {}

    @Override
    public Shape[][] shape(Cell cell, Watershed watershed, ChunkPos chunk) {
        CellPos cellPos = cell.pos();
        CellPos downstreamPos = watershed.downstream(cellPos);
        Set<CellPos> upstreamSet = watershed.upstream(cellPos);

        if (upstreamSet.size() < 2) {
            return null;
        }

        int cellSize = CommonConfig.get().cellSize();
        int center = cellSize / 2;
        int centerY = cell.y();

        CellCache cache = CellCache.get();

        List<EntryInfo> entries = new ArrayList<>();
        for (CellPos upstreamPos : upstreamSet) {
            Direction dir = getDirection(cellPos, upstreamPos);
            int[] point = getEdgePoint(dir, cellSize, center);
            Cell upstreamCell = cache.getOrCompute(upstreamPos);
            int entryY = (centerY + upstreamCell.y()) / 2;
            entries.add(new EntryInfo(point, entryY));
        }

        Direction exitDir = getDirection(cellPos, downstreamPos);
        if (exitDir == Direction.NONE) {
            exitDir = Direction.SOUTH;
        }
        int[] exitPoint = getEdgePoint(exitDir, cellSize, center);

        int exitY;
        if (downstreamPos != null) {
            Cell downstreamCell = cache.getOrCompute(downstreamPos);
            exitY = (centerY + downstreamCell.y()) / 2;
        } else {
            exitY = centerY;
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

                double minDistance = Double.MAX_VALUE;
                int waterY = centerY;
                boolean isEntrySegment = true;

                for (EntryInfo entry : entries) {
                    double d = distanceToSegment(cellLocalX, cellLocalZ,
                        entry.point[0], entry.point[1], center, center);
                    if (d < minDistance) {
                        minDistance = d;
                        double t = projectOntoSegment(cellLocalX, cellLocalZ,
                            entry.point[0], entry.point[1], center, center);
                        waterY = (int) Math.round(entry.y + (centerY - entry.y) * t);
                        isEntrySegment = true;
                    }
                }

                double exitDist = distanceToSegment(cellLocalX, cellLocalZ,
                    center, center, exitPoint[0], exitPoint[1]);
                if (exitDist < minDistance) {
                    minDistance = exitDist;
                    double t = projectOntoSegment(cellLocalX, cellLocalZ,
                        center, center, exitPoint[0], exitPoint[1]);
                    waterY = (int) Math.round(centerY + (exitY - centerY) * t);
                    isEntrySegment = false;
                }

                int slopeWidth = DEPTH - 1;
                double waterEdge = FLAT_RADIUS + slopeWidth;

                if (minDistance > EMBANKMENT_RADIUS) {
                    continue;
                }

                int surfaceY;
                double weight;
                Integer fillWaterY = null;
                boolean isRiverbed;

                if (minDistance <= FLAT_RADIUS) {
                    surfaceY = waterY - DEPTH;
                    fillWaterY = waterY;
                    isRiverbed = true;
                    weight = 1.0;
                } else if (minDistance <= waterEdge) {
                    int stepsFromFlat = (int) Math.ceil(minDistance - FLAT_RADIUS);
                    if (stepsFromFlat == 1) {
                        surfaceY = waterY - DEPTH + 2;
                    } else {
                        surfaceY = waterY - DEPTH + 2 + (stepsFromFlat - 1);
                    }
                    fillWaterY = waterY;
                    isRiverbed = true;
                    weight = 1.0;
                } else {
                    Sample blockSample = SampleCache.get().getOrCompute(worldX, worldZ);
                    if (blockSample.isOcean()) {
                        continue;
                    }

                    double progress = (minDistance - waterEdge) / (EMBANKMENT_RADIUS - waterEdge);
                    weight = 1.0 - progress;
                    surfaceY = waterY;
                    isRiverbed = false;
                }

                result[localX][localZ] = new Shape(surfaceY, weight, isRiverbed, fillWaterY);
            }
        }

        return result;
    }

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return watershed != null && watershed.upstream(cell.pos()).size() > 1;
    }

    @Override
    public void fill() {}
}
