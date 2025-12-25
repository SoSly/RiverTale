package org.sosly.rivertale.cell;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;

public class Grid {

    private final int rows;
    private final int cols;
    private final Cell[] cells;

    public Grid(int size) {
        this.rows = size;
        this.cols = size;
        this.cells = new Cell[size * size];
    }

    public Grid(int rows, int cols) {
        this.rows = rows;
        this.cols = cols;
        this.cells = new Cell[rows * cols];
    }

    public static Grid create(RegionPos regionPos) {
        return create(regionPos, RiverConfig.CELLS_PER_REGION.get());
    }

    public static Grid create(RegionPos regionPos, int size) {
        return Grid.create(regionPos, size, size);
    }

    public static Grid create(RegionPos regionPos, int rows, int cols) {
        Grid grid = new Grid(rows, cols);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                CellPos pos = CellPos.fromLocal(regionPos, row, col, rows, cols);
                grid.set(row, col, new Cell(pos));
            }
        }
        return grid;
    }

    public void copyFlowsFrom(Grid other) {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                cells[row * rows + col].setFlowDirection(other.getFlowAt(row, col));
            }
        }
    }

    public int size() {
        return rows; // todo: this is VERY WRONG
    }

    public Cell get(int row, int col) {
        return cells[row * rows + col];
    }

    public Cell get(CellPos pos) {
        return get(pos.row(), pos.col());
    }

    public void set(int row, int col, Cell cell) {
        cells[row * rows + col] = cell;
    }

    public Direction getFlowAt(int row, int col) {
        return cells[row * rows + col].flowDirection();
    }

    public Direction getOuterEdge(Direction dir, int pos) {
        int edgeIndex = rows - 1;
        return switch (dir) {
            case NORTH -> getFlowAt(0, pos);
            case SOUTH -> getFlowAt(edgeIndex, pos);
            case EAST -> getFlowAt(pos, edgeIndex);
            case WEST -> getFlowAt(pos, 0);
            default -> Direction.NONE;
        };
    }

    public Direction getNeighborEdge(Direction dirFromUs, int pos) {
        int edgeIndex = rows - 1;
        return switch (dirFromUs) {
            case NORTH -> getFlowAt(edgeIndex, pos);
            case SOUTH -> getFlowAt(0, pos);
            case EAST -> getFlowAt(pos, 0);
            case WEST -> getFlowAt(pos, edgeIndex);
            default -> Direction.NONE;
        };
    }
}
