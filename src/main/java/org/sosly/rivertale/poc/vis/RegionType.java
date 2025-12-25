package org.sosly.rivertale.poc.vis;

public enum RegionType {
    OCEAN,
    COASTAL,
    FLUVIAL,
    INLAND;

    private static final int GRID_SIZE = 4;

    public static RegionType get(OceanFinder finder, int originX, int originZ, int regionSize) {
        int regionX = Math.floorDiv(originX, regionSize);
        int regionZ = Math.floorDiv(originZ, regionSize);

        RegionType selfType = classifyRegion(finder, regionX, regionZ, regionSize);
        if (selfType != INLAND) {
            return selfType;
        }

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                RegionType neighborType = classifyRegion(finder, regionX + dx, regionZ + dz, regionSize);
                if (neighborType == COASTAL) {
                    return FLUVIAL;
                }
            }
        }

        return INLAND;
    }

    private static RegionType classifyRegion(OceanFinder finder, int regionX, int regionZ, int regionSize) {
        int minChunkX = (regionX * regionSize) >> 4;
        int maxChunkX = ((regionX + 1) * regionSize) >> 4;
        int minChunkZ = (regionZ * regionSize) >> 4;
        int maxChunkZ = ((regionZ + 1) * regionSize) >> 4;

        int gridWidth = maxChunkX - minChunkX;
        int gridHeight = maxChunkZ - minChunkZ;
        int cellWidth = gridWidth / GRID_SIZE;
        int cellHeight = gridHeight / GRID_SIZE;

        int oceanCount = 0;
        int landCount = 0;

        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                int sampleX = minChunkX + (gx * cellWidth) + (cellWidth / 2);
                int sampleZ = minChunkZ + (gz * cellHeight) + (cellHeight / 2);

                if (finder.isOceanChunk(sampleX, sampleZ)) {
                    oceanCount++;
                } else {
                    landCount++;
                }
            }
        }

        if (landCount == 0) {
            return OCEAN;
        }
        if (oceanCount == 0) {
            return INLAND;
        }
        return COASTAL;
    }
}
