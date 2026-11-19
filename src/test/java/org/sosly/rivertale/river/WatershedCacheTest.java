package org.sosly.rivertale.river;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.core.CellPos;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class WatershedCacheTest {
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
}
