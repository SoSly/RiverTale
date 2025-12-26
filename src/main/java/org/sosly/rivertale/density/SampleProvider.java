package org.sosly.rivertale.density;

import net.minecraft.world.level.ChunkPos;

@FunctionalInterface
public interface SampleProvider {
    Sample sample(ChunkPos pos);
}
