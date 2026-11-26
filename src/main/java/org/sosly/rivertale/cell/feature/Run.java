package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;

public class Run implements FeatureHandler {
    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        if (watershed == null) {
            return false;
        }
        return watershed.contains(cell.pos());
    }

    @Override
    public List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        if (entries.size() > 1) {
            throw new IllegalArgumentException("A Run cannot have multiple upstream cells");
        }
        if (entries.isEmpty() && exit.isEmpty()) {
            return List.of();
        }
        return List.of(new RunSpline(cell, entries.stream().findFirst(), exit, meanderSeed));
    }
}
