package org.sosly.rivertale.density;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleTest {
    @Test
    void retainsNativeOceanFlagIndependentlyOfContinentalnessAndHeight() {
        Sample ocean = new Sample(new ChunkPos(0, 0), 0.8, 140, true);
        Sample land = new Sample(new ChunkPos(1, 0), -0.8, -20, false);

        assertTrue(ocean.isOcean());
        assertFalse(land.isOcean());
        assertEquals(140, ocean.terrainHeight());
        assertEquals(-20, land.terrainHeight());
    }

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
