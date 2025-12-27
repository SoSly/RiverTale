package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.Watershed;

public class PlungePool extends AbstractCourseHandler {
    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        if (watershed == null) {
            return false;
        }

        var upstreamCells = watershed.upstream(cell.pos());
        if (upstreamCells.size() != 1) {
            return false;
        }

        CellPos upstreamPos = upstreamCells.iterator().next();
        Cell upstream = CellCache.get().getOrCompute(upstreamPos);
        return upstream.feature() == Feature.WATERFALL;
    }
}
