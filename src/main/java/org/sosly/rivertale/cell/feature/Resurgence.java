package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public class Resurgence implements FeatureHandler {
    private static final double EROSION_THRESHOLD = 0.5;
    private static final double DEPTH_THRESHOLD = 0.2;

    public Shape[][] shape(Cell cell, Watershed watershed, ChunkPos pos) {
        return null;
    }

    public boolean classify(Cell cell, Watershed watershed) {
        if (cell.flowDirections().isEmpty()) {
            return false;
        }

        Sample sample = cell.sample();
        return sample.erosion() > EROSION_THRESHOLD && sample.depth() > DEPTH_THRESHOLD;
    }

    public void fill() {}
}
