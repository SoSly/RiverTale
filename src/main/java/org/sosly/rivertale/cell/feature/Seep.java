package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Seep implements FeatureHandler {
    private static final double VEGETATION_THRESHOLD = 0.4;
    private static final double EROSION_THRESHOLD = 0.3;

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        if (cell.hasInflowingNeighbor()) {
            return false;
        }
        if (cell.averageVegetation() <= VEGETATION_THRESHOLD) {
            return false;
        }
        if (cell.averageErosion() >= EROSION_THRESHOLD) {
            return false;
        }
        return true;
    }
}
