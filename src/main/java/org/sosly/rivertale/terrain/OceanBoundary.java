package org.sosly.rivertale.terrain;

import net.minecraft.nbt.CompoundTag;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;

public record OceanBoundary(CellPos land, CellPos ocean) {
    public FlowDirection bearing() {
        if (ocean.x() > land.x()) {
            return FlowDirection.EAST;
        }
        if (ocean.x() < land.x()) {
            return FlowDirection.WEST;
        }
        if (ocean.z() > land.z()) {
            return FlowDirection.SOUTH;
        }
        if (ocean.z() < land.z()) {
            return FlowDirection.NORTH;
        }
        return FlowDirection.NONE;
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
