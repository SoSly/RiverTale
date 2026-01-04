package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public class Seep implements FeatureHandler {
    private static final double VEGETATION_THRESHOLD = 0.4;
    private static final double EROSION_THRESHOLD = 0.3;

    public Shape[][] shape(Cell cell, Watershed watershed, ChunkPos pos) {
        return null;
    }

    public boolean classify(Cell cell, Watershed watershed) {
        if (cell.flowDirections().isEmpty()) {
            return false;
        }
        if (Cell.hasUpstreamNeighbor(cell.pos(), SampleCache.get())) {
            return false;
        }

        Sample sample = cell.sample();
        return sample.vegetation() > VEGETATION_THRESHOLD && sample.erosion() < EROSION_THRESHOLD;
    }

    public void fill() {}
}
