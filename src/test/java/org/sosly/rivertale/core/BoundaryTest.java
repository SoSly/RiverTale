package org.sosly.rivertale.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundaryTest {

    @Test
    void directionNorthWhenWaterIsAboveLand() {
        CellPos land = new CellPos(5, 5);
        CellPos water = new CellPos(5, 4);
        Boundary boundary = new Boundary(land, water, BoundaryType.OCEAN);

        assertEquals(FlowDirection.NORTH, boundary.direction());
    }

    @Test
    void directionSouthWhenWaterIsBelowLand() {
        CellPos land = new CellPos(5, 5);
        CellPos water = new CellPos(5, 6);
        Boundary boundary = new Boundary(land, water, BoundaryType.OCEAN);

        assertEquals(FlowDirection.SOUTH, boundary.direction());
    }

    @Test
    void directionEastWhenWaterIsRightOfLand() {
        CellPos land = new CellPos(5, 5);
        CellPos water = new CellPos(6, 5);
        Boundary boundary = new Boundary(land, water, BoundaryType.OCEAN);

        assertEquals(FlowDirection.EAST, boundary.direction());
    }

    @Test
    void directionWestWhenWaterIsLeftOfLand() {
        CellPos land = new CellPos(5, 5);
        CellPos water = new CellPos(4, 5);
        Boundary boundary = new Boundary(land, water, BoundaryType.OCEAN);

        assertEquals(FlowDirection.WEST, boundary.direction());
    }

    @Test
    void constructorThrowsForNonAdjacentCells() {
        CellPos land = new CellPos(5, 5);
        CellPos water = new CellPos(7, 5);

        assertThrows(IllegalArgumentException.class, () -> new Boundary(land, water, BoundaryType.OCEAN));
    }
}
