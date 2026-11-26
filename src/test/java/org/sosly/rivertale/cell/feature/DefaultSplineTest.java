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

class DefaultSplineTest {
    private final Cell cell = cell(0, 0, 54);
    private final Cell west = cell(-1, 0, 80);
    private final Cell northeast = cell(1, -1, 70);
    private final Cell south = cell(0, 1, 48);
    private final int size = CommonConfig.get().cellBlocks();
    private final Vec3 center = new Vec3(size / 2, 54, size / 2);

    @Test
    void fallbackHasSeparateIncomingLegsAndOnlyOneSharedExit() {
        List<RiverSpline> paths = Feature.NONE.handler.buildSplines(cell, List.of(west, northeast), Optional.of(south), 0);
        assertEquals(3, paths.size());
        assertEquals(new Vec3(0, 54, size / 2), paths.get(0).pointAt(0));
        assertEquals(new Vec3(size, 54, 0), paths.get(1).pointAt(0));
        for (RiverSpline path : paths.subList(0, 2)) {
            assertEquals(center, path.pointAt(1));
            assertEquals(path.pointAt(0).lerp(center, 0.5), path.pointAt(0.5));
        }
        assertEquals(center, paths.get(2).pointAt(0));
        assertEquals(new Vec3(size / 2, 48, size), paths.get(2).pointAt(1));
        assertEquals(48, paths.get(2).pointAt(1 - 1.0 / size).y);
    }

    @Test
    void endpointsDoNotInventAMissingNeighbor() {
        List<RiverSpline> source = Feature.NONE.handler.buildSplines(cell, List.of(), Optional.of(south), 0);
        assertEquals(1, source.size());
        assertEquals(center, source.get(0).pointAt(0));
        List<RiverSpline> outlet = Feature.NONE.handler.buildSplines(cell, List.of(west), Optional.empty(), 0);
        assertEquals(1, outlet.size());
        assertEquals(center, outlet.get(0).pointAt(1));
        assertTrue(Feature.NONE.handler.buildSplines(cell, List.of(), Optional.empty(), 0).isEmpty());
    }

    private static Cell cell(int x, int z, int y) {
        return new Cell(new CellPos(x, z), Set.of()).withCellY(y);
    }
}
