package org.sosly.rivertale.core;

import org.sosly.rivertale.config.RiverConfig;

public record RegionPos(int x, int z) {

    public static RegionPos at(int x, int z) {
        int regionSize = getRegionSize();
        int regionX = Math.floorDiv(x, regionSize);
        int regionZ = Math.floorDiv(z, regionSize);
        return new RegionPos(regionX, regionZ);
    }

    public static int getRegionSize() {
        return RiverConfig.REGION_SIZE.get();
    }

    public RegionPos relative(Direction direction) {
        return new RegionPos(x + direction.dx, z + direction.dz);
    }

    public int worldX() {
        return x * getRegionSize();
    }

    public int worldZ() {
        return z * getRegionSize();
    }

    public int centerX() {
        return worldX() + getRegionSize() / 2;
    }

    public int centerZ() {
        return worldZ() + getRegionSize() / 2;
    }
}
