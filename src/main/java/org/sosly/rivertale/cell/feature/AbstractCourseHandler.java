package org.sosly.rivertale.cell.feature;

import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public abstract class AbstractCourseHandler implements FeatureHandler {
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

        FlowDirection exitDir = getDirection(cellPos, downstreamPos);
        FlowDirection entryDir = getEntryDirection(cellPos, upstreamSet);

        if (entryDir == FlowDirection.NONE) {
            entryDir = exitDir.opposite();
        }
        if (exitDir == FlowDirection.NONE) {
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

        Shape[][] result = new Shape[16][16];

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunk.getMinBlockX() + localX;
                int worldZ = chunk.getMinBlockZ() + localZ;

                int cellLocalX = worldX - cellMinX;
                int cellLocalZ = worldZ - cellMinZ;

                PathInfo pathInfo = getPathInfo(cellLocalX, cellLocalZ, entryPoint, exitPoint, center, center);
                double distance = pathInfo.distance();

                Sample blockSample = SampleCache.get().getOrCompute(worldX, worldZ);
                if (blockSample.isOcean()) {
                    continue;
                }

                double segmentLength = pathInfo.isEntrySegment() ? entrySegmentLength : exitSegmentLength;
                FlowDirection flowDir = pathInfo.isEntrySegment() ? entryDir.opposite() : exitDir;
                int[] segmentStart;
                int[] segmentEnd;
                if (pathInfo.isEntrySegment()) {
                    segmentStart = entryPoint;
                    segmentEnd = new int[]{center, center};
                } else {
                    segmentStart = new int[]{center, center};
                    segmentEnd = exitPoint;
                }
                FlowInfo flowInfo = computeFlowInfo(
                    pathInfo, entryY, centerY, exitY, segmentLength,
                    flowDir, cellLocalX, cellLocalZ, segmentStart, segmentEnd
                );
                int waterY = flowInfo.waterY();
                Integer flowLevel = flowInfo.flowLevel();

                int vanillaY = blockSample.estimatedHeight();

                ProfileResult profile = calculateProfile(distance, waterY, vanillaY);
                if (profile == null) {
                    continue;
                }

                double overflow = 0;
                if (pathInfo.isEntrySegment()) {
                    double tUnclamped = projectOntoSegmentUnclamped(
                        cellLocalX, cellLocalZ, entryPoint[0], entryPoint[1], center, center
                    );
                    if (tUnclamped < 0) {
                        overflow = -tUnclamped * entrySegmentLength;
                    }
                } else {
                    double tUnclamped = projectOntoSegmentUnclamped(
                        cellLocalX, cellLocalZ, center, center, exitPoint[0], exitPoint[1]
                    );
                    if (tUnclamped > 1) {
                        overflow = (tUnclamped - 1) * exitSegmentLength;
                    }
                }
                double effectiveOverflow = Math.max(0, overflow - PARALLEL_GRACE_ZONE);
                double parallelAttenuation = 1.0 / (1.0 + effectiveOverflow / PARALLEL_DECAY_DISTANCE);

                boolean inChannel = distance <= DEFAULT_WIDTH / 2;
                Integer shapeFlowLevel = profile.isRiverbed() && inChannel ? flowLevel : null;
                FlowDirection flowDirection = profile.isRiverbed() && inChannel
                    ? (pathInfo.isEntrySegment() ? entryDir.opposite() : exitDir)
                    : null;
                double attenuatedWeight = profile.weight() * parallelAttenuation;
                result[localX][localZ] = new Shape(
                    profile.surfaceY(),
                    attenuatedWeight,
                    profile.isRiverbed(),
                    profile.waterY(),
                    shapeFlowLevel,
                    flowDirection,
                    false
                );
            }
        }

        return result;
    }

    @Override
    public void fill() {
    }
}
