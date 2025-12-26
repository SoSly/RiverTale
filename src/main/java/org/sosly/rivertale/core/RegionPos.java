package org.sosly.rivertale.core;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.sosly.rivertale.config.CommonConfig;

public record RegionPos(int x, int z) {
    private static final long REGION_PACKING_MASK = 0xFFFFFFFFL;

    public RegionPos(long packedPos) {
        this((int) packedPos, (int) (packedPos >> Integer.SIZE));
    }

    public RegionPos(BlockPos pos) {
        this(pos.getX() >> bits(), pos.getZ() >> bits());
    }

    public RegionPos(CellPos pos) {
        this(pos.x() >> cellsPerRegionBits(), pos.z() >> cellsPerRegionBits());
    }

    public long toLong() {
        return asLong(x, z);
    }

    public static long asLong(BlockPos pos) {
        return asLong(pos.getX() >> bits(), pos.getZ() >> bits());
    }

    public static long asLong(int x, int z) {
        return (long) x & REGION_PACKING_MASK | ((long) z & REGION_PACKING_MASK) << Integer.SIZE;
    }

    public int getMinBlockX() {
        return regionToBlockCoord(this.x);
    }

    public int getMinBlockZ() {
        return regionToBlockCoord(this.z);
    }

    public int getMaxBlockX() {
        return regionToBlockCoord(this.x) + size();
    }

    public int getMaxBlockZ() {
        return regionToBlockCoord(this.z) + size();
    }

    public int getMiddleBlockX() {
        return regionToBlockCoord(this.x) + size() / 2;
    }

    public int getMiddleBlockZ() {
        return regionToBlockCoord(this.z) + size() / 2;
    }

    public CellPos getMinCell() {
        int shift = cellsPerRegionBits();
        return new CellPos(this.x << shift, this.z << shift);
    }

    public CellPos getMaxCell() {
        int shift = cellsPerRegionBits();
        int cells = cellsPerRegion();
        return new CellPos((this.x << shift) + cells - 1, (this.z << shift) + cells - 1);
    }

    public BlockPos getWorldPosition(int y) {
        return new BlockPos(this.getMinBlockX(), y, this.getMinBlockZ());
    }

    public RegionPos relative(Direction dir) {
        return new RegionPos(x + dir.dx, z + dir.dz);
    }

    public boolean contains(CellPos cell) {
        return cell.getRegion().equals(this);
    }

    @NotNull
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    private static int regionToBlockCoord(int coord) {
        return coord << bits();
    }

    private static int size() {
        return CommonConfig.get().regionSize();
    }

    private static int bits() {
        return Integer.numberOfTrailingZeros(size());
    }

    private static int cellsPerRegion() {
        return CommonConfig.get().cellsPerRegion();
    }

    private static int cellsPerRegionBits() {
        return Integer.numberOfTrailingZeros(cellsPerRegion());
    }
}
