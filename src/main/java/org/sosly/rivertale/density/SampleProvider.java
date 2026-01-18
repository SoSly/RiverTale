package org.sosly.rivertale.density;

import net.minecraft.world.level.ChunkPos;

public interface SampleProvider {
    Sample sampleCore(ChunkPos pos);
    Sample sampleFull(ChunkPos pos);
}
