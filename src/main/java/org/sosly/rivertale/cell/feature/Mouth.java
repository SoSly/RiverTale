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

public class Mouth implements FeatureHandler {
    private static final int SEA_LEVEL = 63;
    private static final int WIDTH = 12;
    private static final int DEPTH = 5;
    private static final int INFLUENCE_RADIUS = 120;

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

        CellCache cache = CellCache.get();

        CellPos upstreamPos = upstreamSet.iterator().next();
        Cell upstreamCell = cache.getOrCompute(upstreamPos);
        int entryY = (cell.y() + upstreamCell.y()) / 2;

        int centerY = cell.y();
        int exitY = SEA_LEVEL;

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

                if (pathInfo.distance() > INFLUENCE_RADIUS) {
                    continue;
                }

                int waterY = interpolateY(pathInfo, entryY, centerY, exitY);

                double weight = 1.0 - (pathInfo.distance() / INFLUENCE_RADIUS);
                boolean isRiverbed = pathInfo.distance() <= WIDTH / 2.0;

                // Mouth only shapes the riverbed, not surrounding terrain
                if (!isRiverbed) {
                    continue;
                }

                int surfaceY = waterY - DEPTH;
                result[localX][localZ] = new Shape(surfaceY, weight, true, waterY);
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
