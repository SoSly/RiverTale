package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.core.CellPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatershedBuilderTest {
    @Test
    void mergesSharedSuffixesWithoutCountingDuplicateFlowlines() {
        Flowline first = path(0, 0, 1, 1, 1, 2);
        Flowline second = path(2, 0, 1, 1, 1, 2);
        List<Watershed> result = WatershedBuilder.build(List.of(first, second, first));

        assertEquals(1, result.size());
        Watershed watershed = result.get(0);
        assertEquals(4, watershed.allCells().size());
        assertEquals(Set.of(new CellPos(0, 0), new CellPos(2, 0)), watershed.upstream(new CellPos(1, 1)));
        assertEquals(3, watershed.accumulation(new CellPos(1, 2)));
        verify(result, List.of(first, second));
    }

    @Test
    void absorbsTheSmallerCrossingRiverAndRemovesOnlyItsAbandonedTail() {
        Flowline main = path(3, 0, 2, 0, 1, 0, 0, 1, 0, 2);
        Flowline tributary = path(2, 2, 1, 1, 0, 0);
        List<Watershed> result = WatershedBuilder.build(List.of(main, tributary));

        assertEquals(1, result.size());
        Watershed watershed = result.get(0);
        assertEquals(new CellPos(0, 1), watershed.downstream(new CellPos(1, 1)));
        assertFalse(watershed.contains(new CellPos(0, 0)));
        assertEquals(6, watershed.accumulation(new CellPos(0, 2)));
        assertEquals(snapshot(result), snapshot(WatershedBuilder.build(List.of(tributary, main))));
        verify(result, List.of(main, tributary));
    }

    @Test
    void keepsTributariesThatJoinAnOtherwiseAbandonedTail() {
        Flowline main = path(3, 0, 2, 0, 1, 0, 0, 1, 0, 2);
        Flowline crossing = path(2, 2, 1, 1, 0, 0, -1, 0);
        Flowline other = path(-2, 1, -1, 1, -1, 0);
        List<Watershed> result = WatershedBuilder.build(List.of(main, crossing, other));

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(watershed -> watershed.contains(new CellPos(-2, 1))));
        assertFalse(result.stream().anyMatch(watershed -> watershed.contains(new CellPos(0, 0))));
        verify(result, List.of(main, crossing, other));
    }

    @Test
    void joinsDivergentPathsUsingTheirUniqueUpstreamSupport() {
        Flowline main = path(-1, 3, 0, 3, 1, 3, 2, 3, 3, 3, 4, 3);
        Flowline other = path(2, 5, 2, 4, 2, 3, 2, 2, 2, 1);
        List<Watershed> result = WatershedBuilder.build(List.of(main, other));

        assertEquals(1, result.size());
        assertEquals(new CellPos(3, 3), result.get(0).downstream(new CellPos(2, 3)));
        assertFalse(result.get(0).contains(new CellPos(2, 2)));
        verify(result, List.of(main, other));
    }

    @Test
    void ignoresFailedPathsAndSupportsIsolatedOutlets() {
        Flowline invalid = new Flowline(List.of(new CellPos(0, 0)), null, false);
        Flowline single = path(10, 10);
        assertTrue(WatershedBuilder.build(List.of(invalid)).isEmpty());
        List<Watershed> result = WatershedBuilder.build(List.of(invalid, single));
        assertEquals(1, result.size());
        assertNull(result.get(0).downstream(new CellPos(10, 10)));
    }

    @Test
    void overlappingNetworksStayConnectedAndIndependentOfInputOrder() {
        Random random = new Random(18743);
        for (int trial = 0; trial < 30; trial++) {
            List<Flowline> paths = new ArrayList<>();
            for (int n = 0; n < 20; n++) {
                List<CellPos> cells = new ArrayList<>();
                CellPos current = new CellPos(random.nextInt(12), random.nextInt(12));
                cells.add(current);
                for (int step = 0; step < 16; step++) {
                    CellPos next = new CellPos(current.x() + random.nextInt(3) - 1, current.z() + random.nextInt(3) - 1);
                    if (!cells.contains(next)) {
                        cells.add(next);
                        current = next;
                    }
                }
                paths.add(new Flowline(cells, current, true));
            }
            List<Watershed> result = WatershedBuilder.build(paths);
            verify(result, paths);
            Collections.shuffle(paths, random);
            assertEquals(snapshot(result), snapshot(WatershedBuilder.build(paths)));
        }
    }

    private void verify(List<Watershed> watersheds, List<Flowline> paths) {
        Set<CellPos> all = new HashSet<>();
        Map<CellPos, CellPos> links = snapshot(watersheds);
        for (Watershed watershed : watersheds) {
            List<CellPos> ordered = watershed.outletToSources();
            assertEquals(watershed.allCells().size(), ordered.size());
            CellPos outlet = ordered.get(0);
            assertNull(watershed.downstream(outlet));
            assertEquals(ordered.size() - 1, watershed.accumulation(outlet));
            for (CellPos cell : ordered) {
                assertTrue(all.add(cell), "Watersheds must not share a cell");
                assertEquals(outlet, watershed.terminus(cell).orElseThrow());
                CellPos next = watershed.downstream(cell);
                if (next == null) {
                    continue;
                }
                assertTrue(watershed.upstream(next).contains(cell));
                assertTrue(ordered.indexOf(next) < ordered.indexOf(cell));
                int dx = next.x() - cell.x();
                int dz = next.z() - cell.z();
                assertTrue(Math.abs(dx) <= 1 && Math.abs(dz) <= 1);
                if (dx != 0 && dz != 0) {
                    CellPos acrossX = new CellPos(next.x(), cell.z());
                    CellPos acrossZ = new CellPos(cell.x(), next.z());
                    assertFalse(acrossX.equals(links.get(acrossZ)) || acrossZ.equals(links.get(acrossX)));
                }
            }
        }
        for (Flowline path : paths) {
            assertTrue(all.contains(path.cells().get(0)), "Every successful original source must survive");
        }
    }

    private Map<CellPos, CellPos> snapshot(List<Watershed> watersheds) {
        Map<CellPos, CellPos> links = new HashMap<>();
        for (Watershed watershed : watersheds) {
            for (CellPos cell : watershed.allCells()) {
                links.put(cell, watershed.downstream(cell));
            }
        }
        return links;
    }

    private Flowline path(int... coordinates) {
        List<CellPos> cells = new ArrayList<>();
        for (int i = 0; i < coordinates.length; i += 2) {
            cells.add(new CellPos(coordinates[i], coordinates[i + 1]));
        }
        return new Flowline(cells, cells.get(cells.size() - 1), true);
    }
}
