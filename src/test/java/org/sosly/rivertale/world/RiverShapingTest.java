package org.sosly.rivertale.world;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionTypeCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RiverShapingTest {
    private static final CellPos CENTER = new CellPos(1000, -1000);
    private CommonConfig originalConfig;
    private TestSampleProvider provider;
    private RandomState overworldState;
    private Holder<Biome> riverBiome;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, 10, 1, 3, 288, false, false, 16));
        RiverShaping.shutdown();
        SampleCache.shutdown();
        CellCache.shutdown();
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        provider = new TestSampleProvider();
        SampleCache.init(provider);
        CellCache.init();
        overworldState = mock(RandomState.class);
        riverBiome = mock(Holder.class);
    }

    @AfterEach
    void tearDown() {
        RiverShaping.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
        CommonConfig.set(originalConfig);
    }

    @Test
    void initializationMakesRtfSeaLevelAvailableBeforeAnyChunk() {
        RiverShaping.init(riverBiome, 78, overworldState);

        assertEquals(78, RiverShaping.getSeaLevel());
        assertSame(riverBiome, RiverShaping.getRiverBiome());
        assertSame(overworldState, RiverShaping.getRandomState());
        assertTrue(RiverShaping.isActive(overworldState));
    }

    @Test
    void uninitializedWorldDoesNotSampleOrClassify() {
        RiverShaping.shape(chunkAt(CENTER), 63, overworldState);

        assertNull(CellCache.get().getIfPresent(CENTER));
        assertTrue(provider.requested.isEmpty());
    }

    @Test
    void foreignDimensionStateDoesNotSampleOrClassify() {
        RiverShaping.init(riverBiome, 78, overworldState);

        RiverShaping.shape(chunkAt(CENTER), 32, mock(RandomState.class));

        assertNull(CellCache.get().getIfPresent(CENTER));
        assertTrue(provider.requested.isEmpty());
        assertEquals(78, RiverShaping.getSeaLevel());
    }

    @Test
    void activeInlandChunkClassifiesOnlyItsCellWithoutRegionDiscovery() {
        RiverShaping.init(riverBiome, 78, overworldState);

        RiverShaping.shape(chunkAt(CENTER), 63, overworldState);

        Cell classified = CellCache.get().getIfPresent(CENTER);
        assertEquals(Feature.SNOWMELT, classified.feature());
        assertEquals(180, classified.averageEstimatedTerrainHeight());
        assertEquals(78, RiverShaping.getSeaLevel());
        for (FlowDirection direction : FlowDirection.D8) {
            Cell neighbor = CellCache.get().getIfPresent(CENTER.relative(direction));
            assertEquals(Feature.NONE, neighbor.feature());
        }
        assertTrue(provider.requested.stream().allMatch(pos -> Math.abs(pos.x - CENTER.x()) <= 2 && Math.abs(pos.z - CENTER.z()) <= 2));
    }

    @Test
    void oceanChunkRemainsUnclassified() {
        provider.ocean = true;
        RiverShaping.init(riverBiome, 78, overworldState);

        RiverShaping.shape(chunkAt(CENTER), 63, overworldState);

        Cell cell = CellCache.get().getIfPresent(CENTER);
        assertTrue(cell.isOcean());
        assertEquals(Feature.NONE, cell.feature());
        assertTrue(cell.flowDirections().isEmpty());
    }

    @Test
    void outOfBoundsChunkDoesNotSampleTerrain() {
        RiverShaping.init(riverBiome, 78, overworldState);

        RiverShaping.shape(chunkAt(new CellPos(2_000_000, 0)), 63, overworldState);

        assertTrue(provider.requested.isEmpty());
    }

    @Test
    void shutdownClearsWorldStateAndStopsClassification() {
        RiverShaping.init(riverBiome, 78, overworldState);
        RiverShaping.shutdown();

        RiverShaping.shape(chunkAt(CENTER), 63, overworldState);

        assertFalse(RiverShaping.isActive(overworldState));
        assertNull(RiverShaping.getRandomState());
        assertNull(RiverShaping.getRiverBiome());
        assertEquals(63, RiverShaping.getSeaLevel());
        assertTrue(provider.requested.isEmpty());
    }

    private ChunkAccess chunkAt(CellPos pos) {
        ChunkAccess chunk = mock(ChunkAccess.class);
        when(chunk.getPos()).thenReturn(new ChunkPos(pos.x(), pos.z()));
        return chunk;
    }

    private static class TestSampleProvider implements SampleProvider {
        private final Set<ChunkPos> requested = new HashSet<>();
        private boolean ocean;

        @Override
        public Sample sampleCore(ChunkPos pos) {
            requested.add(pos);
            int height = pos.x == CENTER.x() && pos.z == CENTER.z() ? 180 : 120;
            return new Sample(pos, 0.5, height, ocean, 0.5, 0.1, 0.1, 0.1);
        }

        @Override
        public double sample(DensityField field, ChunkPos pos) {
            throw new AssertionError("Fixture already supplies all terrain fields: " + field);
        }
    }
}
