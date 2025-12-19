package org.sosly.rivertale.worldgen.river;

public enum FlowDirection {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0),
    NONE(0, 0);

    private final int dx;
    private final int dz;

    FlowDirection(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public int dx() {
        return dx;
    }

    public int dz() {
        return dz;
    }

    public RiverCellKey neighbor(RiverCellKey from) {
        if (this == NONE) {
            return null;
        }
        return new RiverCellKey(from.cellX() + dx, from.cellZ() + dz);
    }

    public static FlowDirection fromDelta(int dx, int dz) {
        for (FlowDirection direction : values()) {
            if (direction.dx == dx && direction.dz == dz) {
                return direction;
            }
        }
        return NONE;
    }

    public FlowDirection opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case NONE -> NONE;
        };
    }
}
