package org.sosly.rivertale.path;

// todo Crossings are currently stored per-region (IN on one side, OUT on the other),
//      duplicating data. Refactor to a single Crossing that knows both regions,
//      the shared boundary position, and flow direction.
public record Crossing(int row, int col, Direction direction) {

    public enum Direction {
        IN,
        OUT
    }
}
