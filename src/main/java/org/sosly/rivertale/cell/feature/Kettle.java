package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Kettle extends AbstractCourseHandler {
    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return false;
    }
}
