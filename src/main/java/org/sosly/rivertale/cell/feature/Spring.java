package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Spring implements FeatureHandler {
    private static final int HEIGHT_THRESHOLD = 113;
    private static final double RIDGES_THRESHOLD = 0.5;

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        if (cell.averageEstimatedTerrainHeight() <= HEIGHT_THRESHOLD) {
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
