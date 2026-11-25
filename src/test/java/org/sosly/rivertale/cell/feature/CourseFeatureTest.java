package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.Watershed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CourseFeatureTest {
    private static final CellPos UPPER = new CellPos(0, 0);
    private static final CellPos LOWER = new CellPos(1, 1);
    private final Cell cell = new Cell(UPPER, Set.of());

    @Test
    void dropBandsUseAssignedElevationsWithoutAdjustingForDiagonalDistance() {
        Watershed watershed = mock(Watershed.class);
        when(watershed.downstream(UPPER)).thenReturn(LOWER);
        when(watershed.cellY(LOWER)).thenReturn(OptionalInt.of(100));
        Map<Integer, Feature> cases = Map.of(-1, Feature.NONE, 0, Feature.NONE, 1, Feature.NONE,
            2, Feature.RAPIDS, 4, Feature.RAPIDS, 5, Feature.CASCADE, 11, Feature.CASCADE,
            12, Feature.WATERFALL, 32, Feature.WATERFALL);
        cases.forEach((drop, expected) -> {
            when(watershed.cellY(UPPER)).thenReturn(OptionalInt.of(100 + drop));
            assertDropFeature(cell, watershed, expected);
        });
    }

    @Test
    void missingWatershedDownstreamLinkOrEitherElevationDoesNotMatchADropFeature() {
        assertDropFeature(cell, null, Feature.NONE);
        Watershed watershed = mock(Watershed.class);
        when(watershed.cellY(UPPER)).thenReturn(OptionalInt.of(120));
        assertDropFeature(cell, watershed, Feature.NONE);
        when(watershed.downstream(UPPER)).thenReturn(LOWER);
        when(watershed.cellY(LOWER)).thenReturn(OptionalInt.empty());
        assertDropFeature(cell, watershed, Feature.NONE);
        when(watershed.cellY(LOWER)).thenReturn(OptionalInt.of(100));
        when(watershed.cellY(UPPER)).thenReturn(OptionalInt.empty());
        assertDropFeature(cell, watershed, Feature.NONE);
    }

    @Test
    void runRequiresOnlyWatershedMembershipAndDeferredFeaturesNeverMatch() {
        Watershed watershed = Watershed.fromLinks(LOWER, Map.of(UPPER, LOWER));
        Cell outlet = new Cell(LOWER, Set.of());
        assertTrue(Feature.RUN.handler.classify(cell, watershed));
        assertTrue(Feature.RUN.handler.classify(outlet, watershed));
        assertFalse(Feature.RUN.handler.classify(cell, null));
        assertFalse(Feature.RUN.handler.classify(new Cell(new CellPos(2, 2), Set.of()), watershed));
        assertFalse(Feature.PLUNGE_POOL.handler.classify(outlet, watershed));
        assertFalse(Feature.BRAID.handler.classify(cell, watershed));
    }

    private void assertDropFeature(Cell candidate, Watershed watershed, Feature expected) {
        for (Feature feature : List.of(Feature.RAPIDS, Feature.CASCADE, Feature.WATERFALL)) {
            assertEquals(expected == feature, feature.handler.classify(candidate, watershed), feature.name());
        }
    }
}
