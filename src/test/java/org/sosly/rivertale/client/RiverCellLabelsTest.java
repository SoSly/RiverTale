package org.sosly.rivertale.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiverCellLabelsTest {
    @Test
    void cellYValueIsOrangeWhenRaisedCyanWhenLoweredAndGrayWhenUnchanged() {
        assertEquals(0xFFFFA044, RiverCellLabels.cellYColor(77, 72));
        assertEquals(0xFF00FFFF, RiverCellLabels.cellYColor(66, 72));
        assertEquals(0xFFD0D0D0, RiverCellLabels.cellYColor(72, 72));
    }
}
