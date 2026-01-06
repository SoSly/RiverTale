package org.sosly.rivertale.core;

import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.sosly.rivertale.config.CommonConfig;

public record CellPos(int x, int z) {
    private static final long CELL_PACKING_MASK = 0xFFFFFFFFL;

    public CellPos(long packedPos) {
        this((int) packedPos, (int) (packedPos >> 32));
    }

    public CellPos(BlockPos pos) {
        this(Math.floorDiv(pos.getX(), size()), Math.floorDiv(pos.getZ(), size()));
    }

    public long toLong() {
        return asLong(x, z);
    }

    public static long asLong(BlockPos pos) {
        return asLong(Math.floorDiv(pos.getX(), size()), Math.floorDiv(pos.getZ(), size()));
    }

    public static long asLong(int x, int z) {
        return (long) x & CELL_PACKING_MASK | ((long) z & CELL_PACKING_MASK) << Integer.SIZE;
    }

    public int getBlockX(int x) {
        return cellToBlockCoord(this.x, x);
    }

    public int getBlockZ(int z) {
        return cellToBlockCoord(this.z, z);
    }

    public int getMinBlockX() {
        return cellToBlockCoord(this.x);
    }

    public int getMinBlockZ() {
        return cellToBlockCoord(this.z);
    }

    public int getMaxBlockX() {
        return this.getBlockX(size());
    }

    public int getMaxBlockZ() {
        return this.getBlockZ(size());
    }

    public int getMiddleBlockX() {
        return this.getBlockX(size() / 2);
    }

    public int getMiddleBlockZ() {
        return this.getBlockZ(size() / 2);
    }

    public BlockPos getMiddleBlockPosition(int y) {
        return new BlockPos(this.getMiddleBlockX(), y, this.getMiddleBlockZ());
    }

    public BlockPos getWorldPosition(int y) {
        return new BlockPos(this.getMinBlockX(), y, this.getMinBlockZ());
    }

    public CellPos relative(FlowDirection dir) {
        return new CellPos(x + dir.dx, z + dir.dz);
    }

    public RegionPos getRegion() {
        return new RegionPos(this);
    }

    @NotNull
    public String toString() {
        return "[" + this.x + ", " + this.z + "]";
    }

    private static int cellToBlockCoord(int coord) {
        return coord * size();
    }

    private static int cellToBlockCoord(int coord, int offset) {
        return cellToBlockCoord(coord) + offset;
    }

    private static int size() {
        return CommonConfig.get().cellSize();
    }
}
