package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public class Divide implements FeatureHandler {
    private static final double THRESHOLD = 1.5;

    public Shape[][] shape(Cell cell, Watershed watershed, ChunkPos pos) {
        return null;
    }
    public boolean classify(Cell cell, Watershed watershed) {
        Sample sample = cell.sample();
        return sample.continents() + sample.depth() > THRESHOLD;
    }
    public void fill() {}
}
