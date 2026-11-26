package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.RiverSpline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RunSplineTest {
    @Test
    void bendHasLevelCardinalAndDiagonalJoinsAtDownstreamHeights() {
        Cell west = cell(-1, 0, 96);
        Cell run = cell(0, 0, 54);
        Cell northeast = cell(1, -1, 53);
        int size = CommonConfig.get().cellBlocks();
        RiverSpline bend = spline(west, run, northeast, 42);
        assertEquals(new Vec3(0, 54, size / 2), bend.pointAt(0));
        assertEquals(new Vec3(size, 53, 0), bend.pointAt(1));
        Vec3 start = bend.pointAt(0.001);
        assertEquals(54, start.y);
        assertEquals(size / 2, start.z);
        assertTrue(start.x > 0);
        Vec3 beforeExit = bend.pointAt(0.999);
        assertEquals(53, beforeExit.y);
        assertEquals(size - beforeExit.x, beforeExit.z, 1e-9);
        RiverSpline next = spline(run, northeast, cell(2, -1, 53), 42);
        assertEquals(bend.pointAt(1), next.pointAt(0));
        assertEquals(53, next.pointAt(0.001).y);
    }

    @Test
    void straightRunsMeanderReproduciblyWithoutMovingTheirJoins() {
        Cell west = cell(-1, 0, 55);
        Cell run = cell(0, 0, 54);
        Cell east = cell(1, 0, 53);
        RiverSpline first = spline(west, run, east, 42);
        RiverSpline rebuilt = spline(west, run, east, 42);
        RiverSpline otherSeed = spline(west, run, east, 43);
        int size = CommonConfig.get().cellBlocks();
        for (int i = 0; i <= 100; i++) {
            double t = i / 100.0;
            assertEquals(first.pointAt(t), rebuilt.pointAt(t));
            assertTrue(Math.abs(first.pointAt(t).z - size / 2) <= size / 8.0);
        }
        assertNotEquals(size / 2.0, first.pointAt(0.5).z);
        assertNotEquals(first.pointAt(0.5), otherSeed.pointAt(0.5));
        assertEquals(first.pointAt(0), otherSeed.pointAt(0));
        assertEquals(first.pointAt(1), otherSeed.pointAt(1));
    }

    private static Cell cell(int x, int z, int y) {
        return new Cell(new CellPos(x, z), Set.of()).withCellY(y);
    }

    private static RiverSpline spline(Cell entry, Cell cell, Cell exit, long seed) {
        List<RiverSpline> result = Feature.RUN.handler.buildSplines(cell, List.of(entry), Optional.of(exit), seed);
        assertEquals(1, result.size());
        return result.get(0);
    }
}
