package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;

public class Default implements FeatureHandler {
    public void carve() {}
    public boolean classify(Cell cell) {
        return true;
    }
    public void fill() {}
}
