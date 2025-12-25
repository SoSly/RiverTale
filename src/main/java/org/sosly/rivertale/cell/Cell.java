package org.sosly.rivertale.cell;

import org.sosly.rivertale.cell.features.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;

public class Cell {

    private final CellPos pos;
    private Direction flowDirection;
    private Feature feature;

    public Cell(CellPos pos) {
        this.pos = pos;
        this.flowDirection = Direction.NONE;
        this.feature = Feature.DEFAULT;
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

    public Feature feature() {
        return feature;
    }

    public void setFeature(Feature feature) {
        this.feature = feature;
    }
}
