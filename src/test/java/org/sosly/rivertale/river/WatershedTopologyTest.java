package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.core.CellPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WatershedTopologyTest {
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
