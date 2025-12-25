package org.sosly.rivertale.cell.features;

import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.cell.features.operations.Carve;
import org.sosly.rivertale.cell.features.operations.Fill;

public enum Feature {
    DEFAULT(CellType.NONE, Default::carve, Default::fill),

    // Sources
    SNOWMELT(CellType.SOURCE, Snowmelt::carve, Snowmelt::fill);

    public final Carve carve;
    public final Fill fill;
    public final CellType TYPE;

    Feature(CellType type, Carve carve, Fill fill) {
        this.carve = carve;
        this.fill = fill;
        this.TYPE = type;
    }
}
