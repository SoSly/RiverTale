package org.sosly.rivertale.core;

public enum FlowDirection {
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

    public static final FlowDirection[] D4 = {NORTH, SOUTH, EAST, WEST};
    public static final FlowDirection[] D8 = {NORTH, SOUTH, EAST, WEST, NORTHEAST, NORTHWEST, SOUTHEAST, SOUTHWEST};
    private static final FlowDirection[] COMPASS = {NORTH, NORTHEAST, EAST, SOUTHEAST, SOUTH, SOUTHWEST, WEST, NORTHWEST};

    FlowDirection(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public FlowDirection opposite() {
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

    public FlowDirection rotateToward(FlowDirection target) {
        if (this == NONE || target == NONE || this == target) {
            return this;
        }

        int currentIdx = compassIndex(this);
        int targetIdx = compassIndex(target);

        int diff = (targetIdx - currentIdx + 8) % 8;

        if (diff == 0) {
            return this;
        }

        if (diff <= 4) {
            return COMPASS[(currentIdx + 1) % 8];
        }

        return COMPASS[(currentIdx + 7) % 8];
    }

    public static FlowDirection fromDelta(int dx, int dz) {
        for (FlowDirection dir : D8) {
            if (dir.dx == dx && dir.dz == dz) {
                return dir;
            }
        }
        return NONE;
    }

    public static FlowDirection toward(CellPos from, CellPos to) {
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();

        if (dx == 0 && dz == 0) {
            return NONE;
        }

        int ndx = (dx == 0) ? 0 : (dx > 0) ? 1 : -1;
        int ndz = (dz == 0) ? 0 : (dz > 0) ? 1 : -1;

        return fromDelta(ndx, ndz);
    }

    private static int compassIndex(FlowDirection dir) {
        for (int i = 0; i < COMPASS.length; i++) {
            if (COMPASS[i] == dir) {
                return i;
            }
        }
        return 0;
    }
}
