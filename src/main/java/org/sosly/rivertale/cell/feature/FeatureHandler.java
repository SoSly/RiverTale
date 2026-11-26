package org.sosly.rivertale.cell.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;

public interface FeatureHandler {
    boolean classify(Cell cell, @Nullable Watershed watershed);

    /** Returns independent downstream paths, sharing the outgoing leg when tributaries merge. */
    default List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        if (entries.isEmpty() && exit.isEmpty()) {
            return List.of();
        }
        Vec3 center = CellCrossings.center(cell);
        List<RiverSpline> paths = new ArrayList<>();
        for (Cell entry : entries) {
            Vec3 start = CellCrossings.between(entry, cell);
            paths.add(t -> start.lerp(center, t));
        }
        exit.ifPresent(next -> paths.add(exitLeg(center, CellCrossings.between(cell, next))));
        return List.copyOf(paths);
    }

    private static RiverSpline exitLeg(Vec3 center, Vec3 crossing) {
        double length = Math.hypot(crossing.x - center.x, crossing.z - center.z);
        double levelFrom = 1 - 1 / length;
        return t -> {
            Vec3 position = center.lerp(crossing, t);
            double y = t >= levelFrom ? crossing.y : center.y + (crossing.y - center.y) * t / levelFrom;
            return new Vec3(position.x, y, position.z);
        };
    }
}
