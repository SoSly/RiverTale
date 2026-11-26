package org.sosly.rivertale.cell.feature;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.RiverSpline;

final class SteppedCourse {
    private SteppedCourse() {}

    static List<RiverSpline> build(Cell entry, Cell cell, Cell exit, int pitches, double pitchFraction, long seed) {
        Vec3 start = CellCrossings.between(entry, cell);
        Vec3 center = CellCrossings.center(cell);
        Vec3 end = CellCrossings.between(cell, exit);
        Vec3 first = start.add(center.subtract(start).normalize());
        Vec3 last = end.add(new Vec3(center.x - end.x, 0, center.z - end.z).normalize());
        RiverSpline bend = CourseMeander.apply(cell.pos(), seed, start, end,
            t -> t == 1 ? last : first.lerp(center, t).lerp(center.lerp(last, t), t));
        List<RiverSpline> paths = new ArrayList<>();
        paths.add(t -> t == 1 ? first : start.lerp(first, t));
        double drop = start.y - end.y;
        double shelfStart = 0;
        double y = start.y;
        for (int i = 0; i < pitches; i++) {
            double lip = (i + (1 - pitchFraction) / 2) / pitches;
            double foot = (i + (1 + pitchFraction) / 2) / pitches;
            double nextY = start.y - Math.floor((i + 1) * drop / pitches);
            paths.add(reach(bend, shelfStart, lip, y, y));
            paths.add(reach(bend, lip, foot, y, nextY));
            shelfStart = foot;
            y = nextY;
        }
        paths.add(reach(bend, shelfStart, 1, y, end.y));
        paths.add(t -> t == 1 ? end : last.lerp(end, t));
        return List.copyOf(paths);
    }

    private static RiverSpline reach(RiverSpline bend, double from, double to, double y, double nextY) {
        return t -> {
            Vec3 point = bend.pointAt(t == 1 ? to : from + t * (to - from));
            return new Vec3(point.x, y + t * (nextY - y), point.z);
        };
    }
}
