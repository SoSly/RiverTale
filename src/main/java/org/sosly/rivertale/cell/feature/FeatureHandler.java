package org.sosly.rivertale.cell.feature;

import javax.annotation.Nullable;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

public interface FeatureHandler {
    boolean classify(Cell cell, @Nullable Watershed watershed);

    default Shape[][] shape(Cell cell, Watershed watershed, ChunkPos chunkPos) {
        return null;
    }

    default Fill[][] fill(Cell cell, Watershed watershed, ChunkPos chunkPos) {
        return null;
    }

    default int profile(double t, int entryY, int exitY) {
        return (int) Math.round(entryY + t * (exitY - entryY));
    }
}
