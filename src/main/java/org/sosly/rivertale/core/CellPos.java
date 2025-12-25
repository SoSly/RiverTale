package org.sosly.rivertale.core;

import org.sosly.rivertale.config.RiverConfig;

public record CellPos(int x, int z) {
    public static CellPos at(int x, int z) {
        int cellSize = getCellSize();
        int cellX = Math.floorDiv(x, cellSize);
        int cellZ = Math.floorDiv(z, cellSize);
        return new CellPos(cellX, cellZ);
    }

    public static int getCellSize() {
        return RiverConfig.REGION_SIZE.get() / RiverConfig.CELLS_PER_REGION.get();
    }

    public CellPos relative(Direction direction) {
        return new CellPos(x + direction.dx, z + direction.dz);
    }

    public int worldX() {
        return x * getCellSize();
    }

    public int worldZ() {
        return z * getCellSize();
    }

    public int centerX() {
        return worldX() + getCellSize() / 2;
    }

    public int centerZ() {
        return worldZ() + getCellSize() / 2;
    }

    public static CellPos fromLocal(RegionPos region, int row, int col) {
        return fromLocal(region, row, col, RiverConfig.CELLS_PER_REGION.get());
    }

    public static CellPos fromLocal(RegionPos region, int row, int col, int cellsPerRegion) {
        return fromLocal(region, row, col, cellsPerRegion, cellsPerRegion);
    }

    public static CellPos fromLocal(RegionPos region, int row, int col, int rows, int cols) {
        return new CellPos(
                region.x() * rows + col,
                region.z() * cols + row
        );
    }

    public RegionPos region() {
        int cellsPerRegion = RiverConfig.CELLS_PER_REGION.get();
        return new RegionPos(
            Math.floorDiv(x, cellsPerRegion),
            Math.floorDiv(z, cellsPerRegion)
        );
    }

    public int row() {
        return Math.floorMod(z, RiverConfig.CELLS_PER_REGION.get());
    }

    public int col() {
        return Math.floorMod(x, RiverConfig.CELLS_PER_REGION.get());
    }

}
