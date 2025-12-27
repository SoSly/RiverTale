package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Sinkhole implements FeatureHandler {
    private static final double VEGETATION_THRESHOLD = 0.4;
    private static final double EROSION_THRESHOLD = 0.3;

    public int[][] carve(Cell cell, Watershed watershed, ChunkAccess chunk) {
        return null;
    }

    public boolean classify(Cell cell, Watershed watershed) {
        return false;
    }

    public void fill() {}
}
