package org.sosly.rivertale.worldgen.river;

public enum FlowDirection {
    NORTH,
    SOUTH,
    EAST,
    WEST,
    NORTHEAST,
    NORTHWEST,
    SOUTHEAST,
    SOUTHWEST,
    SINK;

    public int dRow() {
        return switch (this) {
            case NORTH, NORTHEAST, NORTHWEST -> -1;
            case SOUTH, SOUTHEAST, SOUTHWEST -> 1;
            case EAST, WEST, SINK -> 0;
        };
    }

    public int dCol() {
        return switch (this) {
            case EAST, NORTHEAST, SOUTHEAST -> 1;
            case WEST, NORTHWEST, SOUTHWEST -> -1;
            case NORTH, SOUTH, SINK -> 0;
        };
    }

    public FlowDirection opposite() {
        return switch (this) {
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case EAST -> WEST;
            case WEST -> EAST;
            case NORTHEAST -> SOUTHWEST;
            case NORTHWEST -> SOUTHEAST;
            case SOUTHEAST -> NORTHWEST;
            case SOUTHWEST -> NORTHEAST;
            case SINK -> SINK;
        };
    }
}
