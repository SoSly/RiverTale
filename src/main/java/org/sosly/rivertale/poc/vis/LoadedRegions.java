package org.sosly.rivertale.poc.vis;

import java.util.HashMap;
import java.util.Map;
import org.sosly.rivertale.cell.Grid;

public class LoadedRegions {

    private final Map<Long, Region> regions = new HashMap<>();
    private final Map<Long, Grid> flows = new HashMap<>();

    public void put(int regionX, int regionZ, Region region, Grid flow) {
        long key = key(regionX, regionZ);
        regions.put(key, region);
        if (flow != null) {
            flows.put(key, flow);
        }
    }

    public Region getRegion(int regionX, int regionZ) {
        return regions.get(key(regionX, regionZ));
    }

    public Grid getFlow(int regionX, int regionZ) {
        return flows.get(key(regionX, regionZ));
    }

    public boolean hasRegion(int regionX, int regionZ) {
        return regions.containsKey(key(regionX, regionZ));
    }

    public boolean hasFlow(int regionX, int regionZ) {
        return flows.containsKey(key(regionX, regionZ));
    }

    public int regionCount() {
        return regions.size();
    }

    public int flowCount() {
        return flows.size();
    }

    public Map<Long, Grid> flows() {
        return flows;
    }

    private static long key(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
