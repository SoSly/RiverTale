package org.sosly.rivertale.cell.feature;

import java.util.Optional;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.Watershed;

public class Mouth implements FeatureHandler {
    public void carve() {}
    public boolean classify(Cell cell, Watershed watershed) {
        if (watershed == null) {
            return false;
        }

        Optional<CellPos> terminus = watershed.terminus(cell.pos());
        if (terminus.isEmpty()) {
            return false;
        }

        return terminus.get().equals(cell.pos());
    }
    public void fill() {}
}
