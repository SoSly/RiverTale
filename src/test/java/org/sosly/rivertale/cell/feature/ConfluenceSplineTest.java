package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.RiverSpline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfluenceSplineTest {
    private final Cell cell = cell(0, 0, 96);
    private final Cell west = cell(-1, 0, 128);
    private final Cell north = cell(0, -1, 101);
    private final Cell southeast = cell(1, 1, 100);
    private final Cell east = cell(1, 0, 54);

    @Test
    void westContinuesAsOneRunWhileNorthCurvesIntoIt() {
        RiverSpline run = Feature.RUN.handler.buildSplines(cell, List.of(west), Optional.of(east), 42).get(0);
        List<RiverSpline> paths = build(List.of(north, west), Optional.of(east));
        assertEquals(3, paths.size());
        RiverSpline side = paths.get(1);
        Vec3 join = run.pointAt(0.5);
        for (int i = 0; i <= 20; i++) {
            double t = i / 20.0;
            assertEquals(run.pointAt(0.5 * t), paths.get(0).pointAt(t));
            assertEquals(run.pointAt(0.5 + 0.5 * t), paths.get(2).pointAt(t));
        }
        Vec3 start = new Vec3(cell.pos().getMiddleBlockX(), 96, cell.pos().getMinBlockZ());
        assertEquals(start, side.pointAt(0));
        Vec3 nearEntry = side.pointAt(0.001);
        assertEquals(start.x, nearEntry.x);
        assertEquals(start.y, nearEntry.y);
        assertTrue(nearEntry.z > start.z);
        assertEquals(join, side.pointAt(1));
        assertNotEquals(start.lerp(join, 0.5), side.pointAt(0.5));
        Vec3 arriving = join.subtract(side.pointAt(0.9999)).normalize();
        Vec3 leaving = paths.get(2).pointAt(0.0001).subtract(join).normalize();
        assertTrue(arriving.dot(leaving) > 0.999);
        double y = start.y;
        for (int i = 1; i <= 100; i++) {
            double next = side.pointAt(i / 100.0).y;
            assertTrue(next <= y);
            y = next;
        }
    }

    @Test
    void tiedEntrancesAndThirdTributaryProduceTheSameGeometryInAnyOrder() {
        Cell south = cell(0, 1, 97);
        List<RiverSpline> first = build(List.of(north, south, southeast), Optional.of(east));
        List<RiverSpline> reversed = build(List.of(southeast, south, north), Optional.of(east));
        assertEquals(4, first.size());
        Vec3 merge = first.get(3).pointAt(0);
        for (int i = 0; i < 4; i++) {
            for (double t : List.of(0.0, 0.25, 0.5, 0.75, 1.0)) {
                assertEquals(first.get(i).pointAt(t), reversed.get(i).pointAt(t));
            }
            if (i < 3) {
                assertEquals(merge, first.get(i).pointAt(1));
                assertEquals(96, first.get(i).pointAt(0).y);
            }
        }
    }

    @Test
    void terminalConfluenceDoesNotInventAnExit() {
        List<RiverSpline> paths = build(List.of(west, north), Optional.empty());
        Vec3 center = new Vec3(cell.pos().getMiddleBlockX(), 96, cell.pos().getMiddleBlockZ());
        assertEquals(2, paths.size());
        assertEquals(center, paths.get(0).pointAt(1));
        assertEquals(center, paths.get(1).pointAt(1));
    }

    private List<RiverSpline> build(List<Cell> entries, Optional<Cell> exit) {
        return Feature.CONFLUENCE.handler.buildSplines(cell, entries, exit, 42);
    }

    private static Cell cell(int x, int z, int y) {
        return new Cell(new CellPos(x, z), Set.of()).withCellY(y);
    }
}
