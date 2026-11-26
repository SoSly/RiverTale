package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;

public class Rapids implements FeatureHandler {
    private static final double RIFFLE_FRACTION = 2.0 / 3;

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return CourseDrop.classify(cell.pos(), watershed) == Feature.RAPIDS;
    }

    @Override
    public List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        if (entries.size() != 1 || exit.isEmpty()) {
            return FeatureHandler.super.buildSplines(cell, entries, exit, meanderSeed);
        }
        Cell downstream = exit.get();
        int drop = cell.cellY().orElseThrow() - downstream.cellY().orElseThrow();
        return SteppedCourse.build(entries.get(0), cell, downstream, drop, RIFFLE_FRACTION, meanderSeed);
    }
}
