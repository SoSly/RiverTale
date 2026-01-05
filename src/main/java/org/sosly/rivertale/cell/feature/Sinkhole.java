package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Sinkhole extends AbstractCourseHandler {
    private static final double VEGETATION_THRESHOLD = 0.4;
    private static final double EROSION_THRESHOLD = 0.3;

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return false;
    }
}
