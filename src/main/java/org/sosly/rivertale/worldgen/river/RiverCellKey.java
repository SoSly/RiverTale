package org.sosly.rivertale.worldgen.river;

public record RiverCellKey(int cellX, int cellZ, int pass) {

    private static final int BASE_CELL_SIZE = 4096;
    private static final int SUBDIVISION_FACTOR = 7;
    private static final int MINIMUM_CELL_SIZE = 128;

    public static RiverCellKey fromBlockPos(int blockX, int blockZ, int pass) {
        int cellSize = getCellSize(pass);
        int cellX = Math.floorDiv(blockX, cellSize);
        int cellZ = Math.floorDiv(blockZ, cellSize);
        return new RiverCellKey(cellX, cellZ, pass);
    }

    public static int getCellSize(int pass) {
        int size = BASE_CELL_SIZE;
        for (int i = 1; i < pass; i++) {
            size /= SUBDIVISION_FACTOR;
        }
        return Math.max(size, MINIMUM_CELL_SIZE);
    }

    public int worldX() {
        return cellX * getCellSize(pass);
    }

    public int worldZ() {
        return cellZ * getCellSize(pass);
    }

    public int centerX() {
        return worldX() + getCellSize(pass) / 2;
    }

    public int centerZ() {
        return worldZ() + getCellSize(pass) / 2;
    }
}
