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
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseMeanderTest {
    private final int size = CommonConfig.get().cellBlocks();

    @Test
    void definedCoursesWanderBetweenStraightJoinsWithoutMovingTheirHeights() {
        verifyMeander(Feature.RUN, 1);
        verifyMeander(Feature.RAPIDS, 3);
        verifyMeander(Feature.CASCADE, 5);
        verifyMeander(Feature.WATERFALL, 42);
    }

    private void verifyMeander(Feature feature, int drop) {
        List<RiverSpline> paths = paths(feature, drop, 42);
        List<RiverSpline> rebuilt = paths(feature, drop, 42);
        List<RiverSpline> otherSeed = paths(feature, drop, 43);
        Vec3 entry = new Vec3(0, 96, size / 2);
        Vec3 exit = new Vec3(size, 96 - drop, size / 2);
        assertEquals(entry, paths.get(0).pointAt(0));
        assertEquals(exit, paths.get(paths.size() - 1).pointAt(1));
        boolean varied = false;
        for (int i = 0; i < paths.size(); i++) {
            for (int j = 0; j <= 20; j++) {
                double t = j / 20.0;
                Vec3 point = paths.get(i).pointAt(t);
                Vec3 other = otherSeed.get(i).pointAt(t);
                assertEquals(point, rebuilt.get(i).pointAt(t));
                assertEquals(point.y, other.y);
                assertEquals(point.x, other.x);
                assertTrue(Math.abs(point.z - entry.z) <= size / 8.0);
                varied |= point.z != entry.z && point.z != other.z;
            }
        }
        assertTrue(varied, feature.name());
    }

    @Test
    void fallbackRemainsStraightForDifferentSeeds() {
        for (long seed : List.of(42L, 43L)) {
            for (RiverSpline path : paths(Feature.NONE, 5, seed)) {
                assertEquals(size / 2.0, path.pointAt(0.5).z);
            }
        }
    }

    private static List<RiverSpline> paths(Feature feature, int drop, long seed) {
        Cell entry = new Cell(new CellPos(-1, 0), Set.of()).withCellY(100);
        Cell cell = new Cell(new CellPos(0, 0), Set.of()).withCellY(96);
        Cell exit = new Cell(new CellPos(1, 0), Set.of()).withCellY(96 - drop);
        return feature.handler.buildSplines(cell, List.of(entry), Optional.of(exit), seed);
    }
}
