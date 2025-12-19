package org.sosly.rivertale.worldgen.river;

import org.sosly.rivertale.config.RiverConfig;

public record RiverCellKey(int cellX, int cellZ) {

    public static RiverCellKey fromBlockPos(int blockX, int blockZ) {
        int cellSize = getCellSize();
        int cellX = Math.floorDiv(blockX, cellSize);
        int cellZ = Math.floorDiv(blockZ, cellSize);
        return new RiverCellKey(cellX, cellZ);
    }

    public static int getCellSize() {
        return RiverConfig.CELL_SIZE.get();
    }

    public int worldX() {
        return cellX * getCellSize();
    }

    public int worldZ() {
        return cellZ * getCellSize();
    }

    public int centerX() {
        return worldX() + getCellSize() / 2;
    }

    public int centerZ() {
        return worldZ() + getCellSize() / 2;
    }
}
