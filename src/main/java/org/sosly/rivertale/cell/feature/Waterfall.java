package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Waterfall implements FeatureHandler {
    public void carve() {}
    public boolean classify(Cell cell, Watershed watershed) {
        return false;
    }
    public void fill() {}
}
