package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.sosly.rivertale.core.CellPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WatershedTopologyTest {
    @AfterEach
    void tearDown() {
        WatershedCache.shutdown();
    }

    @Test
    void replacesOverlappingWatershedsWithoutKeepingStaleBranches() {
        WatershedCache.init();
        CellPos outlet = new CellPos(0, 0);
        CellPos junction = new CellPos(1, 0);
        CellPos abandoned = new CellPos(2, 0);
        CellPos elsewhere = new CellPos(-100, 0);
        Watershed original = Watershed.fromLinks(outlet, Map.of(abandoned, junction, junction, outlet));
        Watershed unrelated = Watershed.fromLinks(elsewhere, Map.of(new CellPos(-101, 0), elsewhere));
        Watershed replacement = Watershed.fromLinks(outlet, Map.of(junction, outlet));
        WatershedCache cache = WatershedCache.get();
        cache.replace(List.of(original, unrelated));
        assertSame(original, cache.getWatershed(abandoned));

        cache.replace(List.of(replacement));

        assertNull(cache.getWatershed(abandoned));
        assertSame(replacement, cache.getWatershed(junction));
        assertSame(unrelated, cache.getWatershed(elsewhere));
        cache.evictForRegion(outlet.getRegion());
        assertNull(cache.getWatershed(junction));
        assertSame(unrelated, cache.getWatershed(elsewhere));
    }

    @Test
    void visitsTheOutletBeforeEveryTributaryAndCountsUniqueUpstreamCells() {
        CellPos outlet = new CellPos(2, 2);
        CellPos junction = new CellPos(1, 1);
        CellPos first = new CellPos(0, 0);
        CellPos second = new CellPos(2, 0);
        Watershed watershed = Watershed.fromLinks(outlet, Map.of(first, junction, second, junction, junction, outlet));

        assertEquals(List.of(outlet, junction, first, second), watershed.outletToSources());
        assertEquals(Set.of(first, second), watershed.upstream(junction));
        assertEquals(3, watershed.accumulation(outlet));
        assertEquals(2, watershed.accumulation(junction));
        assertEquals(0, watershed.accumulation(first));
        assertEquals(outlet, watershed.terminus(second).orElseThrow());
    }

    @Test
    void rejectsDisconnectedCyclesAndMissingDownstreamCells() {
        CellPos outlet = new CellPos(0, 0);
        CellPos first = new CellPos(1, 0);
        CellPos second = new CellPos(2, 0);

        assertThrows(IllegalArgumentException.class, () -> Watershed.fromLinks(outlet, Map.of(first, second, second, first)));
        assertThrows(IllegalArgumentException.class, () -> Watershed.fromLinks(outlet, Map.of(first, second)));
        assertThrows(IllegalArgumentException.class, () -> Watershed.fromLinks(outlet, Map.of(outlet, first)));
    }

    @Test
    void walksLongNetworksWithoutRecursingOnTheJavaStack() {
        CellPos outlet = new CellPos(0, 0);
        Map<CellPos, CellPos> links = new HashMap<>();
        for (int x = 1; x <= 20000; x++) {
            links.put(new CellPos(x, 0), new CellPos(x - 1, 0));
        }
        Watershed watershed = Watershed.fromLinks(outlet, links);

        assertEquals(20001, watershed.outletToSources().size());
        assertEquals(20000, watershed.accumulation(outlet));
    }
}
