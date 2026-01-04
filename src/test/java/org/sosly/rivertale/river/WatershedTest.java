package org.sosly.rivertale.river;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.world.WorldSettings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WatershedTest {

    @Mock
    CellCache cellCache;

    @Mock
    Sample sample;

    private Cell dummyCell(CellPos pos) {
        return new Cell(pos, sample, Feature.DEFAULT, List.of(), 63);
    }

    private Path mockPath(List<CellPos> cells) {
        Path path = mock(Path.class);
        when(path.cells()).thenReturn(cells);
        return path;
    }

    @BeforeEach
    void setUp() {
        WorldSettings.init(63);
        WatershedCache.init();
        when(cellCache.getOrCompute(any(CellPos.class))).thenAnswer(inv -> {
            CellPos pos = inv.getArgument(0);
            return dummyCell(pos);
        });
    }

    @AfterEach
    void tearDown() {
        WatershedCache.shutdown();
        WorldSettings.shutdown();
    }

    @Test
    void diagonalCrossingCurrentPathLosesMergesIntoExisting() {
        // Path 1 (existing, more accumulation): (3,0) → (2,0) → (1,0) → (0,1) → (0,2)
        // Path 2 (new, less accumulation): (2,2) → (1,1) → (0,0)
        //
        // When Path 2 moves from (1,1) to (0,0) [northwest]:
        // - North of (1,1) = (1,0), which has downstream (0,1) [southwest]
        // - This creates a diagonal crossing
        // - Path 1 has more accumulation at (1,0), so Path 2 should merge into Path 1
        //
        // Expected: (1,1) becomes tributary of (1,0), (0,0) is NOT in watershed

        Path path1 = mockPath(List.of(
            new CellPos(3, 0),
            new CellPos(2, 0),
            new CellPos(1, 0),
            new CellPos(0, 1),
            new CellPos(0, 2)
        ));

        Path path2 = mockPath(List.of(
            new CellPos(2, 2),
            new CellPos(1, 1),
            new CellPos(0, 0)
        ));

        try (MockedStatic<CellCache> mockedCache = mockStatic(CellCache.class)) {
            mockedCache.when(CellCache::get).thenReturn(cellCache);

            Watershed watershed = new Watershed(new RegionPos(0, 0), List.of(path1, path2));

            // Path 1 should be intact from source to terminus
            assertTrue(watershed.contains(new CellPos(3, 0)));
            assertTrue(watershed.contains(new CellPos(2, 0)));
            assertTrue(watershed.contains(new CellPos(1, 0)));
            assertTrue(watershed.contains(new CellPos(0, 1)));
            assertTrue(watershed.contains(new CellPos(0, 2)));

            // Path 2's upstream should be in watershed, merged at crossing
            assertTrue(watershed.contains(new CellPos(2, 2)));
            assertTrue(watershed.contains(new CellPos(1, 1)));

            // Path 2's downstream after crossing should NOT be in watershed
            assertFalse(watershed.contains(new CellPos(0, 0)));

            // (1,1) should flow to (0,1) - the downstream of the crossing cell
            assertEquals(new CellPos(0, 1), watershed.downstream(new CellPos(1, 1)));

            // (0,1) should have both (1,0) and (1,1) as upstream (confluence)
            Set<CellPos> upstreamOf01 = watershed.upstream(new CellPos(0, 1));
            assertTrue(upstreamOf01.contains(new CellPos(1, 0)));
            assertTrue(upstreamOf01.contains(new CellPos(1, 1)));
        }
    }

    @Test
    void diagonalCrossingExistingPathLosesGetsPruned() {
        // Path 1 (existing, less accumulation): (2,0) → (1,0) → (0,1) → (0,2)
        // Path 2 (new, more accumulation): (4,2) → (3,2) → (2,2) → (1,1) → (0,0)
        //
        // When Path 2 moves from (1,1) to (0,0) [northwest]:
        // - North of (1,1) = (1,0), which has downstream (0,1) [southwest]
        // - This creates a diagonal crossing
        // - Path 2 has more accumulation at (1,1), so Path 1's downstream should be pruned
        //
        // Expected: (1,0) becomes tributary of (1,1), (0,1) and (0,2) are removed

        Path path1 = mockPath(List.of(
            new CellPos(2, 0),
            new CellPos(1, 0),
            new CellPos(0, 1),
            new CellPos(0, 2)
        ));

        Path path2 = mockPath(List.of(
            new CellPos(4, 2),
            new CellPos(3, 2),
            new CellPos(2, 2),
            new CellPos(1, 1),
            new CellPos(0, 0)
        ));

        try (MockedStatic<CellCache> mockedCache = mockStatic(CellCache.class)) {
            mockedCache.when(CellCache::get).thenReturn(cellCache);

            Watershed watershed = new Watershed(new RegionPos(0, 0), List.of(path1, path2));

            // Path 2 should be fully intact
            assertTrue(watershed.contains(new CellPos(4, 2)));
            assertTrue(watershed.contains(new CellPos(3, 2)));
            assertTrue(watershed.contains(new CellPos(2, 2)));
            assertTrue(watershed.contains(new CellPos(1, 1)));
            assertTrue(watershed.contains(new CellPos(0, 0)));

            // Path 1's upstream should still be in watershed
            assertTrue(watershed.contains(new CellPos(2, 0)));
            assertTrue(watershed.contains(new CellPos(1, 0)));

            // Path 1's downstream after crossing should be REMOVED
            assertFalse(watershed.contains(new CellPos(0, 1)));
            assertFalse(watershed.contains(new CellPos(0, 2)));

            // (1,0) should now flow to (0,0) - the next cell in the winning path
            assertEquals(new CellPos(0, 0), watershed.downstream(new CellPos(1, 0)));

            // (0,0) should have (1,1) and (1,0) as upstream (confluence)
            Set<CellPos> upstreamOf00 = watershed.upstream(new CellPos(0, 0));
            assertTrue(upstreamOf00.contains(new CellPos(1, 1)));
            assertTrue(upstreamOf00.contains(new CellPos(1, 0)));

            // (0,0) should be terminus (downstream is null)
            assertNull(watershed.downstream(new CellPos(0, 0)));
        }
    }

    @Test
    void noDiagonalCrossingWhenPathsDoNotIntersect() {
        // Two parallel paths that don't cross
        // Path 1: (2,0) → (1,0) → (0,0)
        // Path 2: (2,2) → (1,2) → (0,2)

        Path path1 = mockPath(List.of(
            new CellPos(2, 0),
            new CellPos(1, 0),
            new CellPos(0, 0)
        ));

        Path path2 = mockPath(List.of(
            new CellPos(2, 2),
            new CellPos(1, 2),
            new CellPos(0, 2)
        ));

        try (MockedStatic<CellCache> mockedCache = mockStatic(CellCache.class)) {
            mockedCache.when(CellCache::get).thenReturn(cellCache);

            Watershed watershed = new Watershed(new RegionPos(0, 0), List.of(path1, path2));

            // Both paths should be fully intact
            assertTrue(watershed.contains(new CellPos(2, 0)));
            assertTrue(watershed.contains(new CellPos(1, 0)));
            assertTrue(watershed.contains(new CellPos(0, 0)));
            assertTrue(watershed.contains(new CellPos(2, 2)));
            assertTrue(watershed.contains(new CellPos(1, 2)));
            assertTrue(watershed.contains(new CellPos(0, 2)));

            // Each path should have its own terminus
            assertNull(watershed.downstream(new CellPos(0, 0)));
            assertNull(watershed.downstream(new CellPos(0, 2)));
        }
    }
}
