package org.sosly.rivertale.cell.feature;

import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;

final class CellCrossings {
    private CellCrossings() {}

    static Vec3 center(Cell cell) {
        return new Vec3(cell.pos().getMiddleBlockX(), cell.cellY().orElseThrow(), cell.pos().getMiddleBlockZ());
    }

    static Vec3 between(Cell upstream, Cell downstream) {
        Vec3 to = center(downstream);
        return new Vec3((upstream.pos().getMiddleBlockX() + to.x) / 2, to.y,
            (upstream.pos().getMiddleBlockZ() + to.z) / 2);
    }
}
