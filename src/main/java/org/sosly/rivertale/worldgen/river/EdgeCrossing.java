package org.sosly.rivertale.worldgen.river;

public record EdgeCrossing(int row, int col, Direction direction) {

    public enum Direction {
        IN,
        OUT
    }
}
