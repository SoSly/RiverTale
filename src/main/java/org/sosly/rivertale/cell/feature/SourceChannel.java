package org.sosly.rivertale.cell.feature;

import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.RiverSpline;

final class SourceChannel {
    private final Cell cell;
    private final Vec3 center;
    private final Vec3 crossing;
    private final Vec3 downstream;
    private final Vec3 across;
    private final double size;
    private final long seed;

    SourceChannel(Cell cell, Cell exit, long seed) {
        this.cell = cell;
        this.seed = seed;
        center = CellCrossings.center(cell);
        crossing = CellCrossings.between(cell, exit);
        downstream = new Vec3(crossing.x - center.x, 0, crossing.z - center.z).normalize();
        across = new Vec3(-downstream.z, 0, downstream.x);
        size = cell.pos().getMaxBlockX() - cell.pos().getMinBlockX();
    }

    /** Along is a fraction of the center-to-crossing distance; lateral is a fraction of cell width. */
    Vec3 origin(double along, double lateral) {
        return new Vec3(center.x + (crossing.x - center.x) * along, center.y,
            center.z + (crossing.z - center.z) * along).add(across.scale(lateral * size));
    }

    RiverSpline toExit(Vec3 start) {
        Vec3 last = crossing.subtract(downstream);
        double run = last.subtract(start).dot(downstream);
        Vec3 control = last.subtract(downstream.scale(run / 2));
        double approachFraction = run / (run + 1);
        RiverSpline bend = CourseMeander.apply(cell.pos(), seed, start, crossing, u -> {
            Vec3 point = start.lerp(control, u).lerp(control.lerp(last, u), u);
            return new Vec3(point.x, start.y + (last.y - start.y) * u, point.z);
        });
        return t -> {
            if (t == 1) {
                return crossing;
            }
            if (t >= approachFraction) {
                return last.lerp(crossing, (t - approachFraction) / (1 - approachFraction));
            }
            return bend.pointAt(t / approachFraction);
        };
    }

    RiverSpline rill(RiverSpline channel, double separation, double mergeAt) {
        Vec3 offset = across.scale(separation * size);
        return t -> channel.pointAt(t * mergeAt).add(offset.scale((1 - t) * (1 - t)));
    }
}
