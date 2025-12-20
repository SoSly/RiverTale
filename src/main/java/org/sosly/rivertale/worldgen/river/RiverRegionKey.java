package org.sosly.rivertale.worldgen.river;

import org.sosly.rivertale.config.RiverConfig;

public record RiverRegionKey(int regionX, int regionZ) {

    public static RiverRegionKey fromBlockPos(int blockX, int blockZ) {
        int regionSize = getRegionSize();
        int regionX = Math.floorDiv(blockX, regionSize);
        int regionZ = Math.floorDiv(blockZ, regionSize);
        return new RiverRegionKey(regionX, regionZ);
    }

    public static int getRegionSize() {
        return RiverConfig.REGION_SIZE.get();
    }

    public int worldX() {
        return regionX * getRegionSize();
    }

    public int worldZ() {
        return regionZ * getRegionSize();
    }

    public int centerX() {
        return worldX() + getRegionSize() / 2;
    }

    public int centerZ() {
        return worldZ() + getRegionSize() / 2;
    }
}
