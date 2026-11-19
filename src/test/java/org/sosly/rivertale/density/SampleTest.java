package org.sosly.rivertale.density;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SampleTest {
    @Test
    void enrichmentPreservesNativeHeightAndOceanStatus() {
        Sample core = new Sample(new ChunkPos(-3, 4), -0.5, 41, true);
        Sample enriched = core.withTemperature(-0.7)
            .withVegetation(0.8)
            .withErosion(0.25)
            .withRidges(-0.1);

        assertEquals(new Sample(core.pos(), -0.5, 41, true, 0.25, -0.1, -0.7, 0.8), enriched);
    }
}
