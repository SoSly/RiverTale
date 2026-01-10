package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Sinkhole implements FeatureHandler {
    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return false;
    }
}
