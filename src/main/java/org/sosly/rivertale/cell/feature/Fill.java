package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.core.FlowDirection;

public record Fill(
    int waterY,
    Integer flowLevel,
    FlowDirection flowDirection,
    double weight
) {}
