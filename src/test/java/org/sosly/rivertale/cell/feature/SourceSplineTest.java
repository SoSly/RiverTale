package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.river.RiverSpline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SourceSplineTest {
    private final Cell source = new Cell(new CellPos(-2, 1), Set.of()).withCellY(128);
    private final Cell east = exit(FlowDirection.EAST);

    @Test
    void snowmeltFeedsOneStemFromTwoUphillRills() {
        List<RiverSpline> paths = build(Feature.SNOWMELT, east, 42);
        assertEquals(3, paths.size());
        Vec3 first = paths.get(0).pointAt(0);
        Vec3 second = paths.get(1).pointAt(0);
        assertEquals(128, first.y);
        assertEquals(128, second.y);
        assertTrue(first.x < source.pos().getMiddleBlockX() && second.x < source.pos().getMiddleBlockX());
        assertNotEquals(first.z, second.z);
        Vec3 merge = paths.get(2).pointAt(0);
        assertTrue(merge.y < 128 && merge.y > 96);
        assertEquals(merge, paths.get(0).pointAt(1));
        assertEquals(merge, paths.get(1).pointAt(1));
    }

    @Test
    void springEmergesAtOneOffsetPointWhileSeepStartsCloserToTheExit() {
        List<RiverSpline> spring = build(Feature.SPRING, east, 42);
        List<RiverSpline> seep = build(Feature.SEEP, east, 42);
        assertEquals(1, spring.size());
        assertEquals(1, seep.size());
        Vec3 emergence = spring.get(0).pointAt(0);
        assertNotEquals(source.pos().getMiddleBlockZ(), emergence.z);
        assertTrue(spring.get(0).pointAt(0.05).y < emergence.y);
        assertTrue(emergence.x < source.pos().getMiddleBlockX());
        assertTrue(seep.get(0).pointAt(0).x > source.pos().getMiddleBlockX());
    }

    @Test
    void craterStartsAtALevelSpillwayThenDescends() {
        List<RiverSpline> paths = build(Feature.CRATER, east, 42);
        assertEquals(2, paths.size());
        RiverSpline lip = paths.get(0);
        assertTrue(lip.pointAt(0).x > source.pos().getMiddleBlockX());
        assertTrue(lip.pointAt(1).x > lip.pointAt(0).x);
        assertEquals(128, lip.pointAt(0.5).y);
        assertEquals(lip.pointAt(1), paths.get(1).pointAt(0));
        assertTrue(paths.get(1).pointAt(0.2).y < 128);
    }

    @Test
    void everySourceMeandersInsideTheCellAndMeetsEveryExitLevel() {
        for (Feature feature : List.of(Feature.SPRING, Feature.SNOWMELT, Feature.SEEP, Feature.CRATER)) {
            for (FlowDirection direction : FlowDirection.D8) {
                verifyChannel(feature, exit(direction));
            }
            assertTrue(feature.handler.buildSplines(source, List.of(), Optional.empty(), 42).isEmpty());
        }
    }

    private void verifyChannel(Feature feature, Cell exit) {
        List<RiverSpline> paths = build(feature, exit, 42);
        List<RiverSpline> rebuilt = build(feature, exit, 42);
        List<RiverSpline> other = build(feature, exit, 43);
        boolean varied = false;
        for (int i = 0; i < paths.size(); i++) {
            double y = 128;
            for (int j = 0; j <= 100; j++) {
                double t = j / 100.0;
                Vec3 point = paths.get(i).pointAt(t);
                Vec3 alternate = other.get(i).pointAt(t);
                assertEquals(point, rebuilt.get(i).pointAt(t));
                assertEquals(point.y, alternate.y);
                varied |= !point.equals(alternate);
                assertTrue(point.x >= source.pos().getMinBlockX() && point.x <= source.pos().getMaxBlockX());
                assertTrue(point.z >= source.pos().getMinBlockZ() && point.z <= source.pos().getMaxBlockZ());
                assertTrue(point.y <= y && point.y >= 96);
                y = point.y;
            }
        }
        assertTrue(varied);
        RiverSpline stem = paths.get(paths.size() - 1);
        Vec3 crossing = CellCrossings.between(source, exit);
        assertEquals(crossing, stem.pointAt(1));
        Vec3 near = stem.pointAt(0.999);
        assertEquals(96, near.y);
        Vec3 direction = new Vec3(exit.pos().x() - source.pos().x(), 0, exit.pos().z() - source.pos().z()).normalize();
        assertEquals(1.0, crossing.subtract(near).normalize().dot(direction), 1e-9);
    }

    private Cell exit(FlowDirection direction) {
        return new Cell(source.pos().relative(direction), Set.of()).withCellY(96);
    }

    private List<RiverSpline> build(Feature feature, Cell exit, long seed) {
        return feature.handler.buildSplines(source, List.of(), Optional.of(exit), seed);
    }
}
