package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public abstract class AbstractCourseHandler implements FeatureHandler {
    @Override
    public Shape[][] shape(Cell cell, Watershed watershed, ChunkPos pos) {
        return null;
    }

    @Override
    public void fill() {
    }
}
