package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;

public interface FeatureHandler {
    void carve();
    boolean classify(Cell cell);
    void fill();
}
