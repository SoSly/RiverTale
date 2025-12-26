package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;

public class Seep implements FeatureHandler {
    private static final double VEGETATION_THRESHOLD = 0.4;
    private static final double EROSION_THRESHOLD = 0.3;

    public void carve() {}

    public boolean classify(Cell cell) {
        if (cell.flowDirections().isEmpty()) {
            return false;
        }
        if (Cell.hasUpstreamNeighbor(cell.pos(), SampleCache.get())) {
            return false;
        }

        Sample sample = cell.sample();
        return sample.vegetation() > VEGETATION_THRESHOLD && sample.erosion() < EROSION_THRESHOLD;
    }

    public void fill() {}
}
