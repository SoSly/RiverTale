package org.sosly.rivertale.region;

import java.util.HashSet;
import java.util.Set;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;

public class Region {

    private final RegionPos pos;
    private final double density;
    private final double[][] cellDensities;
    private final boolean participating;

    private Direction primaryOutput;
    private Set<Direction> secondaryOutputs;

    public Region(RegionPos pos, double density, double[][] cellDensities, boolean participating) {
        this.pos = pos;
        this.density = density;
        this.cellDensities = cellDensities;
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

    public double[][] getCellDensities() {
        return cellDensities;
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
