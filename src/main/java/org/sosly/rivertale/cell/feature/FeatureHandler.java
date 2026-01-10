package org.sosly.rivertale.cell.feature;

import javax.annotation.Nullable;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public interface FeatureHandler {
    boolean classify(Cell cell, @Nullable Watershed watershed);
}
