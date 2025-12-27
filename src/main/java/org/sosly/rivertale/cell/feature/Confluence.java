package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Confluence implements FeatureHandler {
    public void carve() {}
    public boolean classify(Cell cell, Watershed watershed) {
        return watershed != null && watershed.upstream(cell.pos()).size() > 1;
    }
    public void fill() {}
}
