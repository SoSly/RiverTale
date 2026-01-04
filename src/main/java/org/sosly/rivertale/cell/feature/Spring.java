package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public class Spring implements FeatureHandler {
    private static final double DEPTH_THRESHOLD = 0.3;
    private static final double RIDGES_THRESHOLD = 0.5;

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
        return sample.depth() > DEPTH_THRESHOLD && sample.ridges() < RIDGES_THRESHOLD;
    }

    public void fill() {}
}
