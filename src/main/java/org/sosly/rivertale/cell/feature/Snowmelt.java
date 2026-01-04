package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public class Snowmelt implements FeatureHandler {
    private static final double THRESHOLD = 1.20;

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
        return sample.continents() + sample.depth() - sample.temperature() > THRESHOLD;
    }

    public void fill() {}
}
