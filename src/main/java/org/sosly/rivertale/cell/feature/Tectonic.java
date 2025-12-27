package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public class Tectonic implements FeatureHandler {
    public int[][] carve(Cell cell, Watershed watershed, ChunkAccess chunk) {
        return null;
    }
    public boolean classify(Cell cell, Watershed watershed) {
        return watershed != null && cell.flowDirections().isEmpty();
    }
    public void fill() {}
}
