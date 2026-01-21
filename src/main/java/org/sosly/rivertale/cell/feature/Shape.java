package org.sosly.rivertale.cell.feature;

public record Shape(
    int y,
    double weight,
    boolean isRiverbed,
    boolean preserveBiome,
    SplinePoint point
) {}
