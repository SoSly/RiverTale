package org.sosly.rivertale.poc.vis;

import org.sosly.rivertale.core.Direction;

public record OceanBoundary(int landX, int landZ, int oceanX, int oceanZ) {
    public int landChunkX() {
        return landX >> 4;
    }

    public int landChunkZ() {
        return landZ >> 4;
    }

    public int oceanChunkX() {
        return oceanX >> 4;
    }

    public int oceanChunkZ() {
        return oceanZ >> 4;
    }

    public Direction bearing() {
        if (oceanX > landX) {
            return Direction.EAST;
        }
        if (oceanX < landX) {
            return Direction.WEST;
        }
        if (oceanZ > landZ) {
            return Direction.SOUTH;
        }
        if (oceanZ < landZ) {
            return Direction.NORTH;
        }
        return Direction.NONE;
    }

    public long asLong() {
        return ((long) (landChunkX() & 0xFFFF) << 48)
             | ((long) (landChunkZ() & 0xFFFF) << 32)
             | ((long) (oceanChunkX() & 0xFFFF) << 16)
             | ((long) (oceanChunkZ() & 0xFFFF));
    }
}
