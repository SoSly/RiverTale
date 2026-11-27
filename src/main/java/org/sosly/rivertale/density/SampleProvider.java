package org.sosly.rivertale.density;

import net.minecraft.world.level.ChunkPos;

public interface SampleProvider {
    Sample sampleCore(ChunkPos pos);

    default int estimatedHeight(int x, int z) {
        return sampleCore(new ChunkPos(Math.floorDiv(x, 16), Math.floorDiv(z, 16))).terrainHeight();
    }

    double sample(DensityField field, ChunkPos pos);
}
