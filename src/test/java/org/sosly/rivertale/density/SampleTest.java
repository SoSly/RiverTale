package org.sosly.rivertale.density;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SampleTest {
    @Test
    void retainsNativeOceanFlagIndependentlyOfContinentalnessAndHeight() {
        Sample ocean = new Sample(new ChunkPos(0, 0), 0.8, 140, true);
        Sample land = new Sample(new ChunkPos(1, 0), -0.8, -20, false);

        assertTrue(ocean.isOcean());
        assertFalse(land.isOcean());
        assertEquals(140, ocean.terrainHeight());
        assertEquals(-20, land.estimatedTerrainHeight());
    }

    @Test
    void enrichmentPreservesNativeHeightAndOceanStatus() {
        Sample core = new Sample(new ChunkPos(-3, 4), -0.5, 41, true);
        Sample enriched = core.withTemperature(-0.7)
            .withVegetation(0.8)
            .withErosion(0.25)
            .withRidges(-0.1);

        assertFalse(core.hasFullDensities());
        assertTrue(enriched.hasFullDensities());
        assertEquals(new Sample(core.pos(), -0.5, 41, true, 0.25, -0.1, -0.7, 0.8), enriched);
    }

    @Test
    void serializationPreservesFullSampleAndNegativeCoordinates() {
        Sample sample = new Sample(new ChunkPos(-24, -3), -0.75, -31, true, 0.2, -0.3, -0.8, 0.45);

        CompoundTag encoded = sample.encode();

        assertEquals(-31, encoded.getInt("terrainHeight"));
        assertTrue(encoded.getBoolean("ocean"));
        assertFalse(encoded.contains("depth"));
        assertEquals(sample, Sample.decode(encoded));
    }

    @Test
    void serializationPreservesMissingAndPartialClimateFields() {
        Sample core = new Sample(new ChunkPos(7, -4), 0.8, 231, false);
        Sample partial = core.withTemperature(-0.5).withRidges(0.75);

        assertEquals(core, Sample.decode(core.encode()));
        assertEquals(partial, Sample.decode(partial.encode()));
    }

    @Test
    void rejectsOldDepthBasedSampleTags() {
        CompoundTag legacy = new CompoundTag();
        legacy.putLong("pos", new ChunkPos(0, 0).toLong());
        legacy.putDouble("continents", 0.5);
        legacy.putDouble("depth", 0.75);

        assertThrows(IllegalArgumentException.class, () -> Sample.decode(legacy));
    }
}
