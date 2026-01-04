package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public class Sinkhole implements FeatureHandler {
    private static final double VEGETATION_THRESHOLD = 0.4;
    private static final double EROSION_THRESHOLD = 0.3;

    public Shape[][] shape(Cell cell, Watershed watershed, ChunkPos pos) {
        return null;
    }

    public boolean classify(Cell cell, Watershed watershed) {
        return false;
    }

    public void fill() {}
}
