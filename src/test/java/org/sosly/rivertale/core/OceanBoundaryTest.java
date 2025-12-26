package org.sosly.rivertale.core;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.terrain.OceanBoundary;


import static org.junit.jupiter.api.Assertions.assertEquals;

class OceanBoundaryTest {

    @Test
    void bearingEastWhenOceanIsEastOfLand() {
        OceanBoundary boundary = new OceanBoundary(new ChunkPos(0, 0), new ChunkPos(1, 0));
        assertEquals(Direction.EAST, boundary.bearing());
    }

    @Test
    void bearingWestWhenOceanIsWestOfLand() {
        OceanBoundary boundary = new OceanBoundary(new ChunkPos(1, 0), new ChunkPos(0, 0));
        assertEquals(Direction.WEST, boundary.bearing());
    }

    @Test
    void bearingSouthWhenOceanIsSouthOfLand() {
        OceanBoundary boundary = new OceanBoundary(new ChunkPos(0, 0), new ChunkPos(0, 1));
        assertEquals(Direction.SOUTH, boundary.bearing());
    }

    @Test
    void bearingNorthWhenOceanIsNorthOfLand() {
        OceanBoundary boundary = new OceanBoundary(new ChunkPos(0, 1), new ChunkPos(0, 0));
        assertEquals(Direction.NORTH, boundary.bearing());
    }

    @Test
    void bearingNoneWhenLandAndOceanAreSamePosition() {
        OceanBoundary boundary = new OceanBoundary(new ChunkPos(5, 5), new ChunkPos(5, 5));
        assertEquals(Direction.NONE, boundary.bearing());
    }
}
