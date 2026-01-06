package org.sosly.rivertale.core;

import org.junit.jupiter.api.Test;
import org.sosly.rivertale.terrain.OceanBoundary;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OceanBoundaryTest {

    @Test
    void bearingEastWhenOceanIsEastOfLand() {
        OceanBoundary boundary = new OceanBoundary(new CellPos(0, 0), new CellPos(1, 0));
        assertEquals(FlowDirection.EAST, boundary.bearing());
    }

    @Test
    void bearingWestWhenOceanIsWestOfLand() {
        OceanBoundary boundary = new OceanBoundary(new CellPos(1, 0), new CellPos(0, 0));
        assertEquals(FlowDirection.WEST, boundary.bearing());
    }

    @Test
    void bearingSouthWhenOceanIsSouthOfLand() {
        OceanBoundary boundary = new OceanBoundary(new CellPos(0, 0), new CellPos(0, 1));
        assertEquals(FlowDirection.SOUTH, boundary.bearing());
    }

    @Test
    void bearingNorthWhenOceanIsNorthOfLand() {
        OceanBoundary boundary = new OceanBoundary(new CellPos(0, 1), new CellPos(0, 0));
        assertEquals(FlowDirection.NORTH, boundary.bearing());
    }

    @Test
    void bearingNoneWhenLandAndOceanAreSamePosition() {
        OceanBoundary boundary = new OceanBoundary(new CellPos(5, 5), new CellPos(5, 5));
        assertEquals(FlowDirection.NONE, boundary.bearing());
    }
}
