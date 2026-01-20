package org.sosly.rivertale.core;

import net.minecraft.nbt.CompoundTag;

public record Boundary(CellPos land, CellPos water, BoundaryType type) {

    public Boundary {
        int dx = water.x() - land.x();
        int dz = water.z() - land.z();
        boolean isD4Adjacent = (Math.abs(dx) == 1 && dz == 0) || (dx == 0 && Math.abs(dz) == 1);
        if (!isD4Adjacent) {
            throw new IllegalArgumentException("Boundary cells must be D4 adjacent: land=" + land + ", water=" + water);
        }
    }

    public FlowDirection direction() {
        int dx = water.x() - land.x();
        int dz = water.z() - land.z();

        if (dx == 1 && dz == 0) {
            return FlowDirection.EAST;
        }
        if (dx == -1 && dz == 0) {
            return FlowDirection.WEST;
        }
        if (dx == 0 && dz == 1) {
            return FlowDirection.SOUTH;
        }
        if (dx == 0 && dz == -1) {
            return FlowDirection.NORTH;
        }

        throw new IllegalStateException("Boundary cells must be D4 adjacent: land=" + land + ", water=" + water);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("land", land.toLong());
        tag.putLong("water", water.toLong());
        tag.putString("type", type.name());
        return tag;
    }

    public static Boundary decode(CompoundTag tag) {
        CellPos land = new CellPos(tag.getLong("land"));
        CellPos water = new CellPos(tag.getLong("water"));
        BoundaryType type = BoundaryType.valueOf(tag.getString("type"));
        return new Boundary(land, water, type);
    }
}
