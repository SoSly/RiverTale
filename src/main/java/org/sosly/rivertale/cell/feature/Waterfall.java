package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.Watershed;

public class Waterfall extends AbstractCourseHandler {
    private static final int MULTIPLIER = 10;

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        if (watershed == null) {
            return false;
        }

        CellPos downstreamPos = watershed.downstream(cell.pos());
        if (downstreamPos == null) {
            return false;
        }

        Cell downstream = CellCache.get().getOrCompute(downstreamPos);
        int drop = cell.y() - downstream.y();
        return drop >= CommonConfig.get().minSlope() * MULTIPLIER;
    }
}
