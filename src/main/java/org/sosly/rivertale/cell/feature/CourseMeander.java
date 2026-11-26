package org.sosly.rivertale.cell.feature;

import java.util.SplittableRandom;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.RiverSpline;

final class CourseMeander {
    private CourseMeander() {}

    static RiverSpline apply(CellPos cell, long seed, Vec3 start, Vec3 end, RiverSpline bend) {
        Vec3 across = new Vec3(start.z - end.z, 0, end.x - start.x).normalize();
        double size = cell.getMaxBlockX() - cell.getMinBlockX();
        double distance = new SplittableRandom(seed ^ cell.toLong()).nextDouble(-0.125, 0.125) * size;
        Vec3 offset = across.scale(distance);
        return t -> {
            // Both the offset and its derivative vanish at the joins.
            double envelope = 4 * t * (1 - t);
            return bend.pointAt(t).add(offset.scale(envelope * envelope));
        };
    }
}
