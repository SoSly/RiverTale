package org.sosly.rivertale.poc.vis;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class Region {
    private final int regionX;
    private final int regionZ;
    private final RegionType type;
    private final Map<Long, OceanBoundary> boundaries = new HashMap<>();
    private OceanBoundary start;
    private int visitedChunks;

    public Region(int regionX, int regionZ, RegionType type) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.type = type;
    }

    public int regionX() {
        return regionX;
    }

    public int regionZ() {
        return regionZ;
    }

    public RegionType type() {
        return type;
    }

    public void put(OceanBoundary boundary) {
        if (start == null) {
            start = boundary;
        }
        boundaries.put(boundary.asLong(), boundary);
    }

    public OceanBoundary start() {
        return start;
    }

    public boolean contains(OceanBoundary boundary) {
        return boundaries.containsKey(boundary.asLong());
    }

    public int size() {
        return boundaries.size();
    }

    public Collection<OceanBoundary> boundaries() {
        return boundaries.values();
    }

    public void setVisitedChunks(int count) {
        this.visitedChunks = count;
    }

    public int visitedChunks() {
        return visitedChunks;
    }

    public int[] getCenter() {
        if (boundaries.isEmpty()) {
            return new int[]{0, 0};
        }

        long sumX = 0;
        long sumZ = 0;
        for (OceanBoundary b : boundaries.values()) {
            sumX += b.landX();
            sumZ += b.landZ();
        }

        int centerX = (int) (sumX / boundaries.size());
        int centerZ = (int) (sumZ / boundaries.size());
        return new int[]{centerX, centerZ};
    }
}
