package org.sosly.rivertale.terrain;

import javax.annotation.Nullable;

public record Shape(
    int y,
    double weight,
    boolean isRiverbed,
    @Nullable Integer waterY
) {}
