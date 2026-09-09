package org.sosly.rivertale.poc.carve;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RiverPathInterpolatorTest {

    @Test
    void configuredSeaLevelMovesTheEntirePathWithoutChangingItsGeometry() {
        RiverPathInterpolator vanillaLevel = new RiverPathInterpolator(
            0, 0, 64, 0, 5, 0, CardinalDirection.EAST, 8, 1234L, 63
        );
        RiverPathInterpolator raisedLevel = new RiverPathInterpolator(
            0, 0, 64, 0, 5, 0, CardinalDirection.EAST, 8, 1234L, 96
        );

        List<RiverPathInterpolator.PathPoint> baseline = vanillaLevel.generatePath();
        List<RiverPathInterpolator.PathPoint> raised = raisedLevel.generatePath();

        assertEquals(116, raised.get(0).targetElevation());
        assertEquals(96, raised.get(raised.size() - 1).targetElevation());
        assertEquals(105, raised.get(32).targetElevation());
        assertEquals(baseline.size(), raised.size());
        assertEquals(vanillaLevel.getWidth(), raisedLevel.getWidth());
        assertEquals(vanillaLevel.getSlope(), raisedLevel.getSlope());

        for (int i = 0; i < baseline.size(); i++) {
            RiverPathInterpolator.PathPoint original = baseline.get(i);
            RiverPathInterpolator.PathPoint shifted = raised.get(i);
            assertEquals(original.x(), shifted.x());
            assertEquals(original.z(), shifted.z());
            assertEquals(original.targetElevation() + 33, shifted.targetElevation());
        }
    }
}
