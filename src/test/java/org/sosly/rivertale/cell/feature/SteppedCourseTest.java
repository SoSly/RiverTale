package org.sosly.rivertale.cell.feature;

import java.util.ArrayList;
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

class SteppedCourseTest {
    private final Cell upstream = cell(-1, 0, 54);
    private final Cell course = cell(0, 0, 53);

    @Test
    void eachRapidsDropProducesThatManySeparateOneBlockPitches() {
        for (int drop = 2; drop <= 4; drop++) {
            List<Integer> pitches = verifyProfile(Feature.RAPIDS, drop);
            assertEquals(drop, pitches.size());
            assertTrue(pitches.stream().allMatch(height -> height == 1));
        }
    }

    @Test
    void everyCascadeHasTwoOrThreeBlockLedgesWithShelvesBetween() {
        for (int drop = 5; drop <= 11; drop++) {
            List<Integer> ledges = verifyProfile(Feature.CASCADE, drop);
            assertTrue(ledges.size() >= 2);
            assertTrue(ledges.stream().allMatch(height -> height == 2 || height == 3));
            if (drop == 5) {
                assertEquals(List.of(2, 3), ledges);
            }
        }
    }

    private List<Integer> verifyProfile(Feature feature, int drop) {
        Cell next = cell(1, -1, 53 - drop);
        List<RiverSpline> paths = feature.handler.buildSplines(course, List.of(upstream), Optional.of(next), 42);
        Vec3 point = new Vec3(course.pos().getMinBlockX(), 53, course.pos().getMiddleBlockZ());
        Vec3 end = new Vec3(course.pos().getMaxBlockX(), 53 - drop, course.pos().getMinBlockZ());
        assertEquals(point.add(1, 0, 0), paths.get(0).pointAt(1));
        List<Integer> pitches = new ArrayList<>();
        boolean previousFell = false;
        for (RiverSpline path : paths) {
            assertEquals(point, path.pointAt(0));
            Vec3 middle = path.pointAt(0.5);
            Vec3 foot = path.pointAt(1);
            assertNotEquals(point, foot);
            assertTrue(point.y >= middle.y && middle.y >= foot.y);
            double fall = point.y - foot.y;
            if (fall > 0) {
                assertTrue(!previousFell, "Two pitches must be separated by a level shelf");
                assertEquals((int) fall, fall);
                pitches.add((int) fall);
            }
            previousFell = fall > 0;
            point = foot;
        }
        assertEquals(end, point);
        Vec3 last = paths.get(paths.size() - 1).pointAt(0);
        assertEquals(end.y, last.y);
        assertEquals(1, last.distanceTo(end), 1e-9);
        assertEquals(end.x - last.x, last.z - end.z, 1e-9);
        return pitches;
    }

    private static Cell cell(int x, int z, int y) {
        return new Cell(new CellPos(x, z), Set.of()).withCellY(y);
    }
}
