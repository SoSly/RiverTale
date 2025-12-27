package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.river.Watershed;

public class Spring implements FeatureHandler {
    private static final double DEPTH_THRESHOLD = 0.3;
    private static final double RIDGES_THRESHOLD = 0.5;

    public void carve() {}

    public boolean classify(Cell cell, Watershed watershed) {
        if (cell.flowDirections().isEmpty()) {
            return false;
        }
        if (Cell.hasUpstreamNeighbor(cell.pos(), SampleCache.get())) {
            return false;
        }

        Sample sample = cell.sample();
        return sample.depth() > DEPTH_THRESHOLD && sample.ridges() < RIDGES_THRESHOLD;
    }

    public void fill() {}
}
