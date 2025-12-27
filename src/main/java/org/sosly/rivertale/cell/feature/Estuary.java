package org.sosly.rivertale.cell.feature;

import java.util.Optional;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.Watershed;

public class Estuary implements FeatureHandler {
    public int[][] carve(Cell cell, Watershed watershed, ChunkAccess chunk) {
        return null;
    }
    public boolean classify(Cell cell, Watershed watershed) {
        if (watershed == null) {
            return false;
        }

        Optional<CellPos> terminus = watershed.terminus(cell.pos());
        if (terminus.isEmpty()) {
            return false;
        }


        if (!terminus.get().equals(cell.pos())) {
            return false;
        }

        return watershed.upstream(cell.pos()).size() > 1;
    }
    public void fill() {}
}
