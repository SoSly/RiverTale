package org.sosly.rivertale.cell.feature;

import java.util.Optional;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.RiverSpline;

final class RunSpline implements RiverSpline {
    private final Vec3 start;
    private final Vec3 end;
    private final Vec3 center;
    private final Vec3 first;
    private final Vec3 last;
    private final RiverSpline bend;
    private final double joinFraction;

    RunSpline(Cell cell, Optional<Cell> entry, Optional<Cell> exit, long seed) {
        center = CellCrossings.center(cell);
        start = entry.map(upstream -> CellCrossings.between(upstream, cell)).orElse(center);
        end = exit.map(downstream -> CellCrossings.between(cell, downstream)).orElse(center);
        Vec3 intoCell = horizontal(center.subtract(start)).normalize();
        Vec3 outOfCell = horizontal(end.subtract(center)).normalize();
        first = start.add(intoCell);
        last = end.subtract(outOfCell);
        double length = first.distanceTo(center) + last.distanceTo(center) + 2;
        joinFraction = 1 / length;
        bend = CourseMeander.apply(cell.pos(), seed, start, end,
            t -> first.lerp(center, t).lerp(center.lerp(last, t), t));
    }

    @Override
    public Vec3 pointAt(double t) {
        if (t == 1) {
            return end;
        }
        if (t <= joinFraction) {
            return start.lerp(first, t / joinFraction);
        }
        if (t >= 1 - joinFraction) {
            return last.lerp(end, (t - (1 - joinFraction)) / joinFraction);
        }
        double u = (t - joinFraction) / (1 - 2 * joinFraction);
        Vec3 point = bend.pointAt(u);
        return new Vec3(point.x, first.y + (last.y - first.y) * u, point.z);
    }

    private static Vec3 horizontal(Vec3 vector) {
        return new Vec3(vector.x, 0, vector.z);
    }
}
