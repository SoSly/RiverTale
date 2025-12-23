package org.sosly.rivertale.cell;

import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;

public class Grid {

    private final int size;
    private final Cell[] cells;

    public Grid(int size) {
        this.size = size;
        this.cells = new Cell[size * size];
    }

    public static Grid create(RegionPos regionPos) {
        return create(regionPos, RiverConfig.CELLS_PER_REGION.get());
    }

    public static Grid create(RegionPos regionPos, int size) {
        Grid grid = new Grid(size);
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                CellPos pos = CellPos.fromLocal(regionPos, row, col, size);
                grid.set(row, col, new Cell(pos));
            }
        }
        return grid;
    }

    public void copyFlowsFrom(Grid other) {
        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                cells[row * size + col].setFlowDirection(other.getFlowAt(row, col));
            }
        }
    }

    public int size() {
        return size;
    }

    public Cell get(int row, int col) {
        return cells[row * size + col];
    }

    public Cell get(CellPos pos) {
        return get(pos.row(), pos.col());
    }

    public void set(int row, int col, Cell cell) {
        cells[row * size + col] = cell;
    }

    public Direction getFlowAt(int row, int col) {
        return cells[row * size + col].flowDirection();
    }

    public Direction getOuterEdge(Direction dir, int pos) {
        int edgeIndex = size - 1;
        return switch (dir) {
            case NORTH -> getFlowAt(0, pos);
            case SOUTH -> getFlowAt(edgeIndex, pos);
            case EAST -> getFlowAt(pos, edgeIndex);
            case WEST -> getFlowAt(pos, 0);
            default -> Direction.NONE;
        };
    }

    public Direction getNeighborEdge(Direction dirFromUs, int pos) {
        int edgeIndex = size - 1;
        return switch (dirFromUs) {
            case NORTH -> getFlowAt(edgeIndex, pos);
            case SOUTH -> getFlowAt(0, pos);
            case EAST -> getFlowAt(pos, 0);
            case WEST -> getFlowAt(pos, edgeIndex);
            default -> Direction.NONE;
        };
    }
}
