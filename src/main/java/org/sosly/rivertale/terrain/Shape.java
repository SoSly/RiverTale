package org.sosly.rivertale.terrain;

import javax.annotation.Nullable;
import org.sosly.rivertale.core.FlowDirection;

public record Shape(
    int y,
    double weight,
    boolean isRiverbed,
    @Nullable Integer waterY,
    @Nullable Integer flowLevel,
    @Nullable FlowDirection flowDirection,
    boolean preserveBiome
) {}
