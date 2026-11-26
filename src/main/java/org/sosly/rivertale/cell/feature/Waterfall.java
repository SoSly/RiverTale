package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;

public class Waterfall implements FeatureHandler {
    private static final double FALL_WIDTH = 2;
    private static final double APPROACH_DROP = 1;

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return CourseDrop.classify(cell.pos(), watershed) == Feature.WATERFALL;
    }

    @Override
    public List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        if (entries.size() != 1 || exit.isEmpty()) {
            return FeatureHandler.super.buildSplines(cell, entries, exit, meanderSeed);
        }
        Vec3 start = CellCrossings.between(entries.get(0), cell);
        Vec3 center = CellCrossings.center(cell);
        Vec3 end = CellCrossings.between(cell, exit.get());
        Vec3 approach = start.add(center.subtract(start).normalize());
        double chord = Math.hypot(end.x - approach.x, end.z - approach.z);
        // On a quadratic bend, points equally spaced around t=0.5 are separated by their t-span times the chord.
        double halfSpan = FALL_WIDTH / (2 * chord);
        double lip = 0.5 - halfSpan;
        double foot = 0.5 + halfSpan;
        double lipY = Math.max(end.y, start.y - APPROACH_DROP);
        RiverSpline bend = CourseMeander.apply(cell.pos(), meanderSeed, start, end,
            t -> approach.lerp(center, t).lerp(center.lerp(end, t), t));
        return List.of(t -> start.lerp(approach, t),
            reach(bend, 0, lip, start.y, lipY),
            reach(bend, lip, foot, lipY, end.y),
            reach(bend, foot, 1, end.y, end.y));
    }

    private static RiverSpline reach(RiverSpline bend, double from, double to, double fromY, double toY) {
        return t -> {
            Vec3 point = bend.pointAt(t == 1 ? to : from + (to - from) * t);
            return new Vec3(point.x, fromY + (toY - fromY) * t, point.z);
        };
    }
}
