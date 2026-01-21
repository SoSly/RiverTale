package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Snowmelt implements FeatureHandler {
    private static final double TEMPERATURE_THRESHOLD = 0.2;
    private static final int HEIGHT_THRESHOLD = 175;

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        if (cell.averageEstimatedTerrainHeight() < HEIGHT_THRESHOLD) {
            return false;
        }
        if (cell.hasInflowingNeighbor()) {
            return false;
        }
        if (cell.averageTemperature() >= TEMPERATURE_THRESHOLD) {
            return false;
        }
        return true;
    }
}
