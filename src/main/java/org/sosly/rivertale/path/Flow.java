package org.sosly.rivertale.path;

import java.util.List;
import java.util.Map;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;

public record Flow(
    Grid flowGrid,
    Crossing[] crossings,
    double[] crossingStrengths,
    Direction primaryOutputDirection,
    List<CellPos> terminusCells,
    Map<Direction, List<CellPos>> riverPaths,
    List<CellPos> confluenceCells
) {
}
