package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Tectonic implements FeatureHandler {
    public void carve() {}
    public boolean classify(Cell cell, Watershed watershed) {
        return watershed != null && cell.flowDirections().isEmpty();
    }
    public void fill() {}
}
