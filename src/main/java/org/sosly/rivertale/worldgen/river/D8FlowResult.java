package org.sosly.rivertale.worldgen.river;

import java.util.List;
import java.util.Map;

public record D8FlowResult(
    FlowDirection[][] flowDirection,
    EdgeCrossing[] crossings,
    double[] crossingStrengths,
    PathDirection primaryOutputDirection,
    int[][] terminusCells,
    Map<PathDirection, List<int[]>> riverPaths,
    List<int[]> confluenceCells
) {
}
