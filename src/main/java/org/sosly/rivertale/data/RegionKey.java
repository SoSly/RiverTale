package org.sosly.rivertale.data;

import net.minecraft.core.BlockPos;

/**
 * Immutable key representing a 512x512 block region (32x32 chunks).
 */
public class RegionKey {
    private static final int REGION_SIZE = 512;

    private final int regionX;
    private final int regionZ;

    public RegionKey(int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
    }

    public static RegionKey fromBlockPos(BlockPos pos) {
        return new RegionKey(
            Math.floorDiv(pos.getX(), REGION_SIZE),
            Math.floorDiv(pos.getZ(), REGION_SIZE)
        );
    }

    public static RegionKey fromBlockCoords(int blockX, int blockZ) {
        return new RegionKey(
            Math.floorDiv(blockX, REGION_SIZE),
            Math.floorDiv(blockZ, REGION_SIZE)
        );
    }

    public int getRegionX() {
        return regionX;
    }

    public int getRegionZ() {
        return regionZ;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RegionKey)) {
            return false;
        }
        RegionKey other = (RegionKey) obj;
        return this.regionX == other.regionX && this.regionZ == other.regionZ;
    }

    @Override
    public int hashCode() {
        return 31 * regionX + regionZ;
    }

    @Override
    public String toString() {
        return "Region(" + regionX + ", " + regionZ + ")";
    }
}

