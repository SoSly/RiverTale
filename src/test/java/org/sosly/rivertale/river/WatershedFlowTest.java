package org.sosly.rivertale.river;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.core.CellPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatershedFlowTest {
    @Test
    void sumsTributariesBeforeAddingOneIncrementAtTheConfluence() {
        CellPos first = new CellPos(0, 0);
        CellPos middle = new CellPos(0, 1);
        CellPos second = new CellPos(1, 1);
        CellPos confluence = new CellPos(0, 2);
        CellPos third = new CellPos(1, 2);
        CellPos outlet = new CellPos(0, 3);
        Watershed topology = Watershed.fromLinks(outlet, Map.of(
            first, middle, middle, confluence, second, confluence, confluence, outlet, third, outlet));

        Watershed watershed = topology.withFlowAccumulation(1.0, 0.5);

        assertEquals(1.0, watershed.flow(first).orElseThrow());
        assertEquals(1.0, watershed.flow(second).orElseThrow());
        assertEquals(1.0, watershed.flow(third).orElseThrow());
        assertEquals(1.5, watershed.flow(middle).orElseThrow());
        assertEquals(3.0, watershed.flow(confluence).orElseThrow());
        assertEquals(4.5, watershed.flow(outlet).orElseThrow());
        assertEquals(5, watershed.accumulation(outlet));
        assertTrue(topology.flow(outlet).isEmpty());
        assertTrue(watershed.flow(new CellPos(2, 0)).isEmpty());
    }

    @Test
    void doesNotAddAnIncrementWhenTheOutletIsAlsoTheSource() {
        CellPos outlet = new CellPos(0, 0);
        Watershed watershed = Watershed.fromLinks(outlet, Map.of()).withFlowAccumulation(1.5, 0.5);

        assertEquals(1.5, watershed.flow(outlet).orElseThrow());
    }

    @Test
    void zeroFlowFromAnUpstreamCellDoesNotMakeTheNextCellASource() {
        CellPos source = new CellPos(0, 0);
        CellPos outlet = new CellPos(0, 1);
        Watershed watershed = Watershed.fromLinks(outlet, Map.of(source, outlet)).withFlowAccumulation(0, 0.5);

        assertEquals(0.0, watershed.flow(source).orElseThrow());
        assertEquals(0.5, watershed.flow(outlet).orElseThrow());
    }
}
