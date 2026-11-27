package org.sosly.rivertale.world;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.cell.feature.FeatureHandler;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.region.RegionTypeCache;
import org.sosly.rivertale.river.PreparedRiverData.CellData;
import org.sosly.rivertale.river.RiverRegion;
import org.sosly.rivertale.river.Watershed;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RiverShapingTest {
    private static final CellPos CENTER = new CellPos(1000, -1000);
    private CommonConfig originalConfig;
    private TestSampleProvider provider;
    private RandomState overworldState;

    @BeforeEach
    void setUp() {
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, false, false, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        RiverShaping.shutdown();
        SampleCache.shutdown();
        CellCache.shutdown();
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        provider = new TestSampleProvider();
        SampleCache.init(provider);
        CellCache.init();
        RegionTypeCache.init();
        RegionCache.init();
        overworldState = mock(RandomState.class);
    }

    @AfterEach
    void tearDown() {
        RiverShaping.shutdown();
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
        CommonConfig.set(originalConfig);
    }

    @Test
    void initializationMakesRtfSeaLevelAvailableBeforeAnyChunk() {
        RiverShaping.init(78, overworldState, 0);

        assertEquals(78, RiverShaping.getSeaLevel());
        assertSame(overworldState, RiverShaping.getRandomState());
        assertTrue(RiverShaping.isActive(overworldState));
    }

    @Test
    void uninitializedWorldDoesNotSampleOrClassify() {
        RiverShaping.shape(chunkAt(CENTER), overworldState);

        assertNull(CellCache.get().getIfPresent(CENTER));
        assertTrue(provider.requested.isEmpty());
    }

    @Test
    void foreignDimensionStateDoesNotSampleOrClassify() {
        RiverShaping.init(78, overworldState, 0);

        RiverShaping.shape(chunkAt(CENTER), mock(RandomState.class));

        assertNull(CellCache.get().getIfPresent(CENTER));
        assertTrue(provider.requested.isEmpty());
        assertEquals(78, RiverShaping.getSeaLevel());
    }

    @Test
    void activeInlandChunkBuildsRiverDataThatSurvivesTerrainCacheEviction() {
        RiverShaping.init(78, overworldState, 0);
        assertNull(RiverShaping.getReady(CENTER.getRegion(), overworldState));

        ChunkAccess chunk = chunkAt(CENTER);
        RiverShaping.shape(chunk, overworldState);

        verify(chunk).getPos();
        verifyNoMoreInteractions(chunk);
        RiverRegion ready = RiverShaping.getReady(CENTER.getRegion(), overworldState);
        assertNotNull(ready);
        assertEquals(Feature.SNOWMELT, ready.cells().get(CENTER).feature());
        assertEquals(180, ready.cells().get(CENTER).height());
        assertEquals(78, RiverShaping.getSeaLevel());
        RegionCache terrain = RegionCache.get();
        RegionPos pos = CENTER.getRegion();
        Region beforeEviction = terrain.getOrCompute(pos);
        for (int i = 1; i <= CacheConfig.DEFAULT.regions(); i++) {
            terrain.put(new Region(new RegionPos(pos.x() + i, pos.z()), RegionType.INLAND));
        }
        assertNotSame(beforeEviction, terrain.getOrCompute(pos));
        CellCache.get().clear();
        SampleCache.get().clear();
        provider.requested.clear();

        RiverShaping.shape(chunkAt(CENTER), overworldState);

        assertTrue(provider.requested.isEmpty());
        assertSame(ready, RiverShaping.getReady(CENTER.getRegion(), overworldState));
    }

    @Test
    void callsNearbyCarverAcrossRegionBorderWhenTheChunksRegionHasNoWatersheds() {
        ChunkPos chunk = new ChunkPos(0, 1);
        RegionPos local = new RegionPos(0, 0);
        RegionPos west = new RegionPos(-1, 0);
        CellPos near = new CellPos(-1, 1);
        CellPos far = new CellPos(-2, 1);
        CellPos unconnected = new CellPos(-1, 0);
        Cell nearRun = new Cell(near, Set.of()).withFeature(Feature.RUN);
        Cell farRun = new Cell(far, Set.of()).withFeature(Feature.RUN);
        Cell unconnectedRun = new Cell(unconnected, Set.of()).withFeature(Feature.RUN);
        CellPos outlet = new CellPos(-1, 2);
        Watershed watershed = Watershed.fromLinks(outlet, Map.of(far, near, near, outlet));
        RiverRegion neighbor = new RiverRegion(new Region(west, RegionType.INLAND), Map.of(
            near, new CellData(nearRun, 120), far, new CellData(farRun, 120),
            unconnected, new CellData(unconnectedRun, 120)), List.of(), List.of(watershed));
        RiverRegion empty = new RiverRegion(new Region(local, RegionType.INLAND), Map.of(), List.of(), List.of());
        Map<RegionPos, RiverRegion> snapshots = Map.of(west, neighbor, local, empty);
        List<RegionPos> requested = new ArrayList<>();
        FeatureHandler handler = mock(FeatureHandler.class);
        when(handler.carve(nearRun, watershed, chunk, 42)).thenCallRealMethod();

        assertTrue(RiverShaping.carveNearby(chunk, 1, 42, pos -> {
            requested.add(pos);
            return snapshots.get(pos);
        }, feature -> {
            assertEquals(Feature.RUN, feature);
            return handler;
        }).isEmpty());

        assertEquals(List.of(west, local), requested);
        verify(handler).carve(nearRun, watershed, chunk, 42);
        verifyNoMoreInteractions(handler);
    }

    @Test
    void rebuildUsesNewTuningForCellClassification() {
        RiverShaping.init(78, overworldState, 0);
        RegionPos region = CENTER.getRegion();
        ChunkAccess chunk = chunkAt(CENTER);
        RiverShaping.shape(chunk, overworldState);
        RiverRegion original = RiverShaping.getReady(region, overworldState);
        assertEquals(Feature.SNOWMELT, original.cells().get(CENTER).feature());

        SourceConfig sources = new SourceConfig(1_000, 0.2, 0.125, 0.5, 0.4, 0.3, 100, 0.2, 0.05);
        CommonConfig tuning = new CommonConfig(8, 1, 1, false, false, 0, 5, 10, sources, CacheConfig.DEFAULT);
        assertTrue(RiverShaping.rebuild(region, overworldState, tuning));
        RiverShaping.shape(chunk, overworldState);

        RiverRegion rebuilt = RiverShaping.getReady(region, overworldState);
        assertSame(tuning, CommonConfig.get());
        assertNotSame(original, rebuilt);
        assertNotEquals(Feature.SNOWMELT, rebuilt.cells().get(CENTER).feature());
    }

    @Test
    void oceanChunkRemainsUnclassified() {
        provider.ocean = true;
        RiverShaping.init(78, overworldState, 0);

        RiverShaping.shape(chunkAt(CENTER), overworldState);

        RiverRegion ready = RiverShaping.getReady(CENTER.getRegion(), overworldState);
        assertNotNull(ready);
        assertEquals(Feature.NONE, ready.cells().get(CENTER).feature());
        assertTrue(ready.flowlines().isEmpty());
        assertTrue(ready.watersheds().isEmpty());
    }

    @Test
    void outOfBoundsChunkDoesNotSampleTerrain() {
        RiverShaping.init(78, overworldState, 0);

        RiverShaping.shape(chunkAt(new CellPos(2_000_000, 0)), overworldState);

        assertTrue(provider.requested.isEmpty());
    }

    @Test
    void shutdownClearsWorldStateAndStopsClassification() {
        RiverShaping.init(78, overworldState, 0);
        RiverShaping.shutdown();

        RiverShaping.shape(chunkAt(CENTER), overworldState);

        assertFalse(RiverShaping.isActive(overworldState));
        assertNull(RiverShaping.getRandomState());
        assertEquals(63, RiverShaping.getSeaLevel());
        assertTrue(provider.requested.isEmpty());
    }

    @Test
    void preclassifyKeepsTheFeatureAssignedByQualify() {
        CellPos cell = new CellPos(CENTER.x() + 1, CENTER.z());
        CellPos outlet = new CellPos(CENTER.x() + 2, CENTER.z());
        Watershed watershed = Watershed.fromLinks(outlet, Map.of(
            CENTER, cell, cell.relative(FlowDirection.NORTH), cell, cell, outlet));

        assertEquals(Feature.NONE, RiverShaping.preclassify(cell).feature());
        assertEquals(Feature.CONFLUENCE, RiverShaping.qualify(cell, watershed).feature());
        assertEquals(Feature.CONFLUENCE, RiverShaping.preclassify(cell).feature());
        assertEquals(Feature.CONFLUENCE, CellCache.get().getIfPresent(cell).feature());
    }

    @Test
    void qualifyKeepsAnExistingFeature() {
        CellPos cell = new CellPos(CENTER.x() + 1, CENTER.z());
        CellPos outlet = new CellPos(CENTER.x() + 2, CENTER.z());
        Watershed watershed = Watershed.fromLinks(outlet, Map.of(cell, outlet));
        Cell candidate = CellCache.get().getOrComputeWithFlow(cell);
        CellCache.get().setFeatureIfNone(candidate.withFeature(Feature.WATERFALL));

        assertEquals(Feature.WATERFALL, RiverShaping.qualify(cell, watershed).feature());
        assertEquals(Feature.WATERFALL, CellCache.get().setFeatureIfNone(candidate.withFeature(Feature.RUN)).feature());
        assertEquals(Feature.WATERFALL, CellCache.get().getIfPresent(cell).feature());
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
