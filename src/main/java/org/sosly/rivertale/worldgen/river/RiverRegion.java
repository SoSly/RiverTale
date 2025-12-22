package org.sosly.rivertale.worldgen.river;

import java.util.HashSet;
import java.util.Set;

public class RiverRegion {

    private final RiverRegionKey key;
    private final double density;
    private final double[][] cellDensities;
    private final boolean participating;

    private PathDirection primaryOutput;
    private Set<PathDirection> secondaryOutputs;

    public RiverRegion(RiverRegionKey key, double density, double[][] cellDensities, boolean participating) {
        this.key = key;
        this.density = density;
        this.cellDensities = cellDensities;
        this.participating = participating;
        this.primaryOutput = PathDirection.NONE;
        this.secondaryOutputs = new HashSet<>();
    }

    public RiverRegionKey getKey() {
        return key;
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

    public PathDirection getPrimaryOutput() {
        return primaryOutput;
    }

    public void setPrimaryOutput(PathDirection primaryOutput) {
        this.primaryOutput = primaryOutput;
    }

    public Set<PathDirection> getSecondaryOutputs() {
        return secondaryOutputs;
    }

    public void setSecondaryOutputs(Set<PathDirection> secondaryOutputs) {
        this.secondaryOutputs = secondaryOutputs;
    }
}
