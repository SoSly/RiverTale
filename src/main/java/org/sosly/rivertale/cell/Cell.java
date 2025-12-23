package org.sosly.rivertale.cell;

import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;

public class Cell {

    private final CellPos pos;
    private Direction flowDirection;

    public Cell(CellPos pos) {
        this.pos = pos;
        this.flowDirection = Direction.NONE;
    }

    public CellPos pos() {
        return pos;
    }

    public int row() {
        return pos.row();
    }

    public int col() {
        return pos.col();
    }

    public Direction flowDirection() {
        return flowDirection;
    }

    public void setFlowDirection(Direction flowDirection) {
        this.flowDirection = flowDirection;
    }
}
