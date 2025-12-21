package org.sosly.rivertale.worldgen.river;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RiverRegion {

    private final RiverRegionKey key;
    private final double density;
    private final double[][] cellDensities;
    private final boolean participating;

    private PathDirection primaryOutput;
    private Set<PathDirection> secondaryOutputs;
    private int distanceToTerminus;
    private Map<PathDirection, List<int[]>> riverPaths;

    public RiverRegion(RiverRegionKey key, double density, double[][] cellDensities, boolean participating) {
        this.key = key;
        this.density = density;
        this.cellDensities = cellDensities;
        this.participating = participating;
        this.primaryOutput = PathDirection.NONE;
        this.secondaryOutputs = new HashSet<>();
        this.distanceToTerminus = -1;
        this.riverPaths = new HashMap<>();
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

    public int getDistanceToTerminus() {
        return distanceToTerminus;
    }

    public void setDistanceToTerminus(int distanceToTerminus) {
        this.distanceToTerminus = distanceToTerminus;
    }

    public Map<PathDirection, List<int[]>> getRiverPaths() {
        return riverPaths;
    }

    public void setRiverPaths(Map<PathDirection, List<int[]>> riverPaths) {
        this.riverPaths = riverPaths;
    }
}
