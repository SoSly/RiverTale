package org.sosly.rivertale.region;

import java.util.HashSet;
import java.util.Set;

import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;

public class Region {

    private final RegionPos pos;
    private final double density;
    private final Grid cells;
    private final boolean participating;

    private Direction primaryOutput;
    private Set<Direction> secondaryOutputs;

    public Region(RegionPos pos, double density, Grid cells, boolean participating) {
        this.pos = pos;
        this.density = density;
        this.cells = cells;
        this.participating = participating;
        this.primaryOutput = Direction.NONE;
        this.secondaryOutputs = new HashSet<>();
    }

    public RegionPos pos() {
        return pos;
    }

    public double getDensity() {
        return density;
    }

    public Grid cells() {
        return cells;
    }

    public boolean isParticipating() {
        return participating;
    }

    public Direction getPrimaryOutput() {
        return primaryOutput;
    }

    public void setPrimaryOutput(Direction primaryOutput) {
        this.primaryOutput = primaryOutput;
    }

    public Set<Direction> getSecondaryOutputs() {
        return secondaryOutputs;
    }

    public void setSecondaryOutputs(Set<Direction> secondaryOutputs) {
        this.secondaryOutputs = secondaryOutputs;
    }
}
