package org.sosly.rivertale.poc.carve;

public enum CardinalDirection {
    NORTH(0, -1),
    SOUTH(0, 1),
    EAST(1, 0),
    WEST(-1, 0);

    public final int dx;
    public final int dz;

    CardinalDirection(int dx, int dz) {
        this.dx = dx;
        this.dz = dz;
    }

    public static CardinalDirection fromString(String s) {
        if (s == null) {
            return null;
        }

        String normalized = s.toLowerCase().trim();

        if (normalized.isEmpty()) {
            return null;
        }

        switch (normalized) {
            case "north":
            case "n":
                return NORTH;
            case "south":
            case "s":
                return SOUTH;
            case "east":
            case "e":
                return EAST;
            case "west":
            case "w":
                return WEST;
            default:
                return null;
        }
    }
}
