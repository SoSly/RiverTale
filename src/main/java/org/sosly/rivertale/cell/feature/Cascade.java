package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;

public class Cascade implements FeatureHandler {
    private static final int MAX_LEDGE_DROP = 3;
    private static final double LEDGE_FRACTION = 0.2;

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return CourseDrop.classify(cell.pos(), watershed) == Feature.CASCADE;
    }

    @Override
    public List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        if (entries.size() != 1 || exit.isEmpty()) {
            return FeatureHandler.super.buildSplines(cell, entries, exit, meanderSeed);
        }
        Cell downstream = exit.get();
        int drop = cell.cellY().orElseThrow() - downstream.cellY().orElseThrow();
        int ledges = (drop + MAX_LEDGE_DROP - 1) / MAX_LEDGE_DROP;
        return SteppedCourse.build(entries.get(0), cell, downstream, ledges, LEDGE_FRACTION, meanderSeed);
    }
}
