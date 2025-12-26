package org.sosly.rivertale.core;

public enum Direction {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0),
    NORTHEAST(1, -1),
    NORTHWEST(-1, -1),
    SOUTHEAST(1, 1),
    SOUTHWEST(-1, 1),
    NONE(0, 0);

    public final int dx;
    public final int dz;

    public static final Direction[] D4 = {NORTH, SOUTH, EAST, WEST};
    public static final Direction[] D8 = {NORTH, SOUTH, EAST, WEST, NORTHEAST, NORTHWEST, SOUTHEAST, SOUTHWEST};

    Direction(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public Direction opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case NORTHEAST -> SOUTHWEST;
            case NORTHWEST -> SOUTHEAST;
            case SOUTHWEST -> NORTHEAST;
            case SOUTHEAST ->  NORTHWEST;
            case NONE -> NONE;
        };
    }
}
