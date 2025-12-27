package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.river.Watershed;

public class Divide implements FeatureHandler {
    private static final double THRESHOLD = 1.5;

    public int[][] carve(Cell cell, Watershed watershed, ChunkAccess chunk) {
        return null;
    }
    public boolean classify(Cell cell, Watershed watershed) {
        Sample sample = cell.sample();
        return sample.continents() + sample.depth() > THRESHOLD;
    }
    public void fill() {}
}
