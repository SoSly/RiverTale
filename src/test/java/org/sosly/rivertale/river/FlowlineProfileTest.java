package org.sosly.rivertale.river;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowlineProfileTest {
    @Test
    void requestedExampleDistinguishesRawFallsFromBothCarvingRules() {
        int[] heights = {55, 54, 59, 52};

        assertEquals(List.of(1L, 0L, 7L), FlowlineProfile.sampledFalls(heights, 52));
        assertEquals(List.of(1L, 0L, 2L), FlowlineProfile.carvedFalls(heights, 52, 0).orElseThrow());
        assertEquals(List.of(1L, 1L, 1L), FlowlineProfile.carvedFalls(heights, 52, 1).orElseThrow());
        assertArrayEquals(new int[] {55, 54, 59, 52}, heights);
    }

    @Test
    void aLargeNaturalDropSurvivesCarvingInsteadOfBecomingAUniformSlope() {
        int[] heights = {100, 99, 105, 80};

        assertEquals(List.of(1L, 0L, 25L), FlowlineProfile.sampledFalls(heights, 80));
        assertEquals(List.of(1L, 0L, 19L), FlowlineProfile.carvedFalls(heights, 80, 0).orElseThrow());
        assertEquals(List.of(1L, 1L, 18L), FlowlineProfile.carvedFalls(heights, 80, 1).orElseThrow());
    }

    @Test
    void consecutiveObstructionsUseThePreviouslyCarvedHeight() {
        int[] heights = {100, 95, 105, 104, 90, 63};

        assertEquals(List.of(5L, 0L, 0L, 5L, 27L), FlowlineProfile.carvedFalls(heights, 63, 0).orElseThrow());
        assertEquals(List.of(5L, 1L, 1L, 3L, 27L), FlowlineProfile.carvedFalls(heights, 63, 1).orElseThrow());
    }

    @Test
    void lowInteriorTerrainCanMakeStrictDescentInfeasibleDespiteEnoughNetFall() {
        int[] heights = {100, 64, 90, 200};

        assertEquals(List.of(36L, 0L, 27L), FlowlineProfile.sampledFalls(heights, 63));
        assertEquals(List.of(36L, 0L, 1L), FlowlineProfile.carvedFalls(heights, 63, 0).orElseThrow());
        assertTrue(FlowlineProfile.carvedFalls(heights, 63, 1).isEmpty());
    }

    @Test
    void neitherCarvingRuleRaisesLowGroundToReachTheOutlet() {
        int[] heights = {100, 60, 90, 80};

        assertTrue(FlowlineProfile.carvedFalls(heights, 63, 0).isEmpty());
        assertTrue(FlowlineProfile.carvedFalls(heights, 63, 1).isEmpty());
    }

    @Test
    void alreadyDescendingProfilesKeepTheirNaturalDrops() {
        int[] heights = {100, 90, 80, 63};
        List<Long> expected = List.of(10L, 10L, 17L);

        assertEquals(expected, FlowlineProfile.sampledFalls(heights, 63));
        assertEquals(expected, FlowlineProfile.carvedFalls(heights, 63, 0).orElseThrow());
        assertEquals(expected, FlowlineProfile.carvedFalls(heights, 63, 1).orElseThrow());
    }

    @Test
    void flatProfilesRemainFlatOnlyWhenZeroDropIsAllowed() {
        int[] heights = {63, 63, 63};

        assertEquals(List.of(0L, 0L), FlowlineProfile.sampledFalls(heights, 63));
        assertEquals(List.of(0L, 0L), FlowlineProfile.carvedFalls(heights, 63, 0).orElseThrow());
        assertTrue(FlowlineProfile.carvedFalls(heights, 63, 1).isEmpty());
    }
}
