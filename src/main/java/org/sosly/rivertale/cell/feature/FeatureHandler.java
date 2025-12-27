package org.sosly.rivertale.cell.feature;

import javax.annotation.Nullable;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public interface FeatureHandler {
    int[][] carve(Cell cell, Watershed watershed, ChunkAccess chunk);
    boolean classify(Cell cell, @Nullable Watershed watershed);
    void fill();
}
