package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Run extends AbstractCourseHandler {
    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return watershed != null;
    }
}
