package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.river.Watershed;

public class Resurgence implements FeatureHandler {
    private static final double EROSION_THRESHOLD = 0.5;
    private static final double DEPTH_THRESHOLD = 0.2;

    public void carve() {}

    public boolean classify(Cell cell, Watershed watershed) {
        if (cell.flowDirections().isEmpty()) {
            return false;
        }

        Sample sample = cell.sample();
        return sample.erosion() > EROSION_THRESHOLD && sample.depth() > DEPTH_THRESHOLD;
    }

    public void fill() {}
}
