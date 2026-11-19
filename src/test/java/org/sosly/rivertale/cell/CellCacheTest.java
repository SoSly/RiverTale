package org.sosly.rivertale.cell;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.CellPos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sample placement is observed through the public contract: the chunks a freshly computed cell samples.
 * Set iteration order is unspecified, so the "center sample is picked first" property of the algorithm
 * is only observable here as "the center is always included, even at the smallest sample count".
 */
class CellCacheTest {
    private static final int CELL_SIZE = 3;

    private CommonConfig originalConfig;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CellCache.shutdown();
    }

    @AfterEach
    void tearDown() {
        CellCache.shutdown();
        CommonConfig.set(originalConfig);
    }

    // A cell that is not at the origin, so the offsets can't be mistaken for absolute chunk coordinates.
    private static final CellPos POS = new CellPos(2, -1);
    private static final int MIN_X = POS.x() * CELL_SIZE;
    private static final int MIN_Z = POS.z() * CELL_SIZE;

    private Set<ChunkPos> samplesFor(int samplesPerCell) {
        CommonConfig.set(new CommonConfig(8, CELL_SIZE, samplesPerCell, false, false, 16, 5, 10,
            SourceConfig.DEFAULT, CacheConfig.DEFAULT, false));
        CellCache.shutdown();
        CellCache.init();
        return CellCache.get().getOrCompute(POS).samplePositions();
    }

    private static ChunkPos chunk(int offsetX, int offsetZ) {
        return new ChunkPos(MIN_X + offsetX, MIN_Z + offsetZ);
    }

    @Test
    void singleSampleIsTheCenterChunk() {
        assertEquals(Set.of(chunk(1, 1)), samplesFor(1));
    }

    @Test
    void fiveSamplesAreTheCenterAndFourCorners() {
        assertEquals(Set.of(chunk(1, 1), chunk(0, 0), chunk(2, 2), chunk(0, 2), chunk(2, 0)), samplesFor(5));
    }

    @Test
    void nineSamplesCoverEveryChunkInTheCell() {
        Set<ChunkPos> expected = new HashSet<>();
        for (int x = 0; x < CELL_SIZE; x++) {
            for (int z = 0; z < CELL_SIZE; z++) {
                expected.add(chunk(x, z));
            }
        }

        assertEquals(expected, samplesFor(9));
    }

    @Test
    void everySampleCountYieldsThatManyDistinctChunksIncludingTheCenter() {
        for (int n = 1; n <= 9; n++) {
            Set<ChunkPos> samples = samplesFor(n);

            // A Set can't hold duplicates, so a duplicated offset would show up as a short count.
            assertEquals(n, samples.size(), "samplesPerCell=" + n);
            assertTrue(samples.contains(chunk(1, 1)), "center missing for samplesPerCell=" + n);
        }
    }
}
