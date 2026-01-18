package org.sosly.rivertale.core;

import java.util.List;

public record Boundary(List<CellPos> cells, BoundaryType type) {}
