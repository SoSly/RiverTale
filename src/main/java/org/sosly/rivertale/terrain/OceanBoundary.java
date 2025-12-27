package org.sosly.rivertale.terrain;

import net.minecraft.nbt.CompoundTag;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;

public record OceanBoundary(CellPos land, CellPos ocean) {
    public Direction bearing() {
        if (ocean.x() > land.x()) {
            return Direction.EAST;
        }
        if (ocean.x() < land.x()) {
            return Direction.WEST;
        }
        if (ocean.z() > land.z()) {
            return Direction.SOUTH;
        }
        if (ocean.z() < land.z()) {
            return Direction.NORTH;
        }
        return Direction.NONE;
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("land", land.toLong());
        tag.putLong("ocean", ocean.toLong());
        return tag;
    }

    public static OceanBoundary decode(CompoundTag tag) {
        return new OceanBoundary(
            new CellPos(tag.getLong("land")),
            new CellPos(tag.getLong("ocean"))
        );
    }
}
