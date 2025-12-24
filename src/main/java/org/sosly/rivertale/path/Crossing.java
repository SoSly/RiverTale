package org.sosly.rivertale.path;

import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;

public class Crossing {

    private final RegionPos source;
    private final RegionPos destination;
    private final int slot;
    private boolean valid = true;

    public Crossing(RegionPos source, RegionPos destination, int slot) {
        this.source = source;
        this.destination = destination;
        this.slot = slot;
    }

    public boolean isSource(RegionPos region) {
        return source.equals(region);
    }

    public boolean isDestination(RegionPos region) {
        return destination.equals(region);
    }

    public CellPos cellFor(RegionPos region) {
        Direction edge = edgeFor(region);
        int edgeIndex = RiverConfig.CELLS_PER_REGION.get() - 1;

        int row = switch (edge) {
            case NORTH -> 0;
            case SOUTH -> edgeIndex;
            case EAST, WEST -> slot;
            default -> -1;
        };

        int col = switch (edge) {
            case NORTH, SOUTH -> slot;
            case EAST -> edgeIndex;
            case WEST -> 0;
            default -> -1;
        };

        return CellPos.fromLocal(region, row, col);
    }

    public Direction edgeFor(RegionPos region) {
        if (source.equals(region)) {
            return directionFrom(source, destination);
        }
        return directionFrom(destination, source);
    }

    private Direction directionFrom(RegionPos from, RegionPos to) {
        if (from.z() > to.z()) {
            return Direction.NORTH;
        }
        if (from.z() < to.z()) {
            return Direction.SOUTH;
        }
        if (from.x() < to.x()) {
            return Direction.EAST;
        }
        return Direction.WEST;
    }

    public void invalidate() {
        valid = false;
    }

    public boolean isValid() {
        return valid;
    }

    public RegionPos source() {
        return source;
    }

    public RegionPos destination() {
        return destination;
    }

    public int slot() {
        return slot;
    }
}
