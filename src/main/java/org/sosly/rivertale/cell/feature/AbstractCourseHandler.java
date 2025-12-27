package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public abstract class AbstractCourseHandler implements FeatureHandler {
    @Override
    public int[][] carve(Cell cell, Watershed watershed, ChunkAccess chunk) {
        return null;
    }

    @Override
    public void fill() {
    }
}
