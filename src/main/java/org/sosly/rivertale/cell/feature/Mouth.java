package org.sosly.rivertale.cell.feature;

import java.util.Optional;
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

        FlowDirection entryDir = getEntryDirection(cellPos, upstreamSet);
        if (entryDir == FlowDirection.NONE) {
            return null;
        }

        int[] entryPoint = getEdgePoint(entryDir, cellSize, center);
        int[] exitPoint = new int[]{center, center};

        double entrySegmentLength = Math.sqrt(
            Math.pow(center - entryPoint[0], 2) + Math.pow(center - entryPoint[1], 2)
        );

        int seaLevel = WorldSettings.get().seaLevel();
        int centerY = seaLevel - 1;

        CellCache cache = CellCache.get();
        CellPos upstreamPos = upstreamSet.iterator().next();
        Cell upstreamCell = cache.getOrCompute(upstreamPos);
        int entryY = (centerY + upstreamCell.y()) / 2;

        int cellMinX = cellPos.getMinBlockX();
        int cellMinZ = cellPos.getMinBlockZ();

        int oceanWaterY = seaLevel - 1;
        SampleCache sampleCache = SampleCache.get();

        Shape[][] result = new Shape[16][16];

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int worldX = chunk.getMinBlockX() + localX;
                int worldZ = chunk.getMinBlockZ() + localZ;

                int cellLocalX = worldX - cellMinX;
                int cellLocalZ = worldZ - cellMinZ;

                PathInfo pathInfo = getPathInfo(cellLocalX, cellLocalZ, entryPoint, exitPoint, center, center);
                double distance = pathInfo.distance();
                FlowDirection flowDir = entryDir.opposite();

                double tUnclamped = projectOntoSegmentUnclamped(
                    cellLocalX, cellLocalZ, entryPoint[0], entryPoint[1], center, center
                );
                double tClamped = Math.max(0, Math.min(1, tUnclamped));
                double embankmentScale = 1.0 - tClamped;

                double perpDistance;
                double terminusOverflow = Math.max(0, (tUnclamped - 1.0) * entrySegmentLength);
                if (tUnclamped > 1.0) {
                    double dirMagnitude = Math.sqrt(flowDir.dx * flowDir.dx + flowDir.dz * flowDir.dz);
                    perpDistance = Math.abs(
                        (cellLocalX - center) * flowDir.dz - (cellLocalZ - center) * flowDir.dx
                    ) / dirMagnitude;
                } else {
                    perpDistance = distance;
                }

                FlowInfo flowInfo = computeFlowInfo(
                    pathInfo, entryY, centerY, centerY, entrySegmentLength,
                    flowDir, cellLocalX, cellLocalZ, entryPoint, exitPoint
                );
                int waterY = tUnclamped > 1.0 ? oceanWaterY : flowInfo.waterY();
                Integer flowLevel = tUnclamped > 1.0 ? null : flowInfo.flowLevel();

                Sample blockSample = sampleCache.getOrCompute(worldX, worldZ);
                int vanillaY = blockSample.estimatedHeight();

                ProfileResult profile = calculateProfile(
                    perpDistance, waterY, vanillaY,
                    DEFAULT_WIDTH, DEFAULT_DEPTH, DEFAULT_DEPTH, embankmentScale
                );
                if (profile == null) {
                    continue;
                }

                double weight = profile.weight();
                if (tUnclamped > 1.0) {
                    double effectiveOverflow = Math.max(0, terminusOverflow - PARALLEL_GRACE_ZONE);
                    double overflowAttenuation = 1.0 / (1.0 + effectiveOverflow / PARALLEL_DECAY_DISTANCE);
                    weight = weight * overflowAttenuation;
                }

                int surfaceY = profile.surfaceY();
                boolean isRiverbed;
                Integer fillWaterY;
                if (tUnclamped > 1.0 && surfaceY <= oceanWaterY) {
                    isRiverbed = true;
                    fillWaterY = oceanWaterY;
                } else {
                    isRiverbed = profile.isRiverbed();
                    fillWaterY = profile.waterY();
                }

                boolean inChannel = perpDistance <= DEFAULT_WIDTH / 2 && tUnclamped <= 1.0;
                Integer shapeFlowLevel = isRiverbed && inChannel ? flowLevel : null;
                FlowDirection shapeFlowDir = shapeFlowLevel != null ? entryDir.opposite() : null;
                result[localX][localZ] = new Shape(
                    surfaceY,
                    weight,
                    isRiverbed,
                    fillWaterY,
                    shapeFlowLevel,
                    shapeFlowDir,
                    true
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
