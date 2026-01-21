package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Spring implements FeatureHandler {
    private static final double DEPTH_THRESHOLD = 0.3;
    private static final double RIDGES_THRESHOLD = 0.5;

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        if (cell.averageDepth() <= DEPTH_THRESHOLD) {
            return false;
        }
        if (cell.hasInflowingNeighbor()) {
            return false;
        }
        if (cell.averageRidges() >= RIDGES_THRESHOLD) {
            return false;
        }
        return true;
    }
}
