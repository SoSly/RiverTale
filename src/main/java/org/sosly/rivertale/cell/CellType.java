package org.sosly.rivertale.cell;

public enum CellType {
    NONE(new float[]{1.0f, 1.0f, 1.0f, 0.1f}),
    SOURCE(new float[]{0.0f, 1.0f, 1.0f, 0.8f}),
    COURSE(new float[]{0.0f, 0.3f, 1.0f, 0.8f}),
    JUNCTION(new float[]{0.0f, 1.0f, 0.0f, 0.8f}),
    LAKE(new float[]{0.2f, 0.5f, 0.9f, 0.8f}),
    TERMINUS(new float[]{0.6f, 0.0f, 0.8f, 0.8f});

    public final float[] color;

    CellType(float[] color) {
        this.color = color;
    }
}
