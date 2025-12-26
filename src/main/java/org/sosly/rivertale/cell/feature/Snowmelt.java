package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;

public class Snowmelt implements FeatureHandler {
    private static final double THRESHOLD = 1.25;

    public void carve() {}

    public boolean classify(Cell cell) {
        if (cell.flowDirection() == Direction.NONE) {
            return false;
        }
        if (Cell.hasUpstreamNeighbor(cell.pos(), SampleCache.get())) {
            return false;
        }

        Sample sample = cell.sample();
        return sample.continents() + sample.depth() - sample.temperature() > THRESHOLD;
    }

    public void fill() {}
}
