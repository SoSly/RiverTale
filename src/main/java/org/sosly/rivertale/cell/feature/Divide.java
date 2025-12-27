package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.river.Watershed;

public class Divide implements FeatureHandler {
    private static final double THRESHOLD = 1.5;

    public void carve() {}
    public boolean classify(Cell cell, Watershed watershed) {
        Sample sample = cell.sample();
        return sample.continents() + sample.depth() > THRESHOLD;
    }
    public void fill() {}
}
