package org.sosly.rivertale.density;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import raccoonman.reterraforged.world.worldgen.GeneratorContext;
import raccoonman.reterraforged.world.worldgen.cell.Cell;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.ControlPoints;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Heightmap;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.Levels;
import raccoonman.reterraforged.world.worldgen.cell.heightmap.WorldLookup;
import raccoonman.reterraforged.world.worldgen.cell.terrain.Terrain;
import raccoonman.reterraforged.world.worldgen.cell.terrain.TerrainType;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.TileCache;
import raccoonman.reterraforged.world.worldgen.densityfunction.tile.generation.TileGenerator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RtfSampleProviderTest {
    private Heightmap heightmap;
    private RtfSampleProvider provider;
    private final List<Cell> terrainCells = new ArrayList<>();
    private final List<Float> terrainX = new ArrayList<>();
    private final List<Float> terrainZ = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Levels levels = new Levels(256, 63);
        heightmap = mock(Heightmap.class);
        when(heightmap.levels()).thenReturn(levels);
        when(heightmap.controlPoints()).thenReturn(new ControlPoints(0.0F, 0.0F, 0.1F, 0.25F, 0.327F, 0.448F, 0.475F, 0.502F));
        doAnswer(invocation -> {
            Cell cell = invocation.getArgument(0);
            terrainCells.add(cell);
            terrainX.add(invocation.getArgument(1));
            terrainZ.add(invocation.getArgument(2));
            cell.height = 0.75F;
            cell.continentEdge = 0.75F;
            cell.terrain = TerrainType.FLATS;
            cell.erosion = 0.125F;
            cell.weirdness = 0.625F;
            return null;
        }).when(heightmap).applyTerrain(any(Cell.class), anyFloat(), anyFloat());
        doAnswer(invocation -> {
            Cell cell = invocation.getArgument(0);
            cell.temperature = -0.75F;
            cell.moisture = 0.375F;
            return null;
        }).when(heightmap).applyClimate(any(Cell.class), anyFloat(), anyFloat(), eq(true));
        doThrow(new AssertionError("River planning must not run during terrain sampling"))
            .when(heightmap).applyRivers(any(Cell.class), anyFloat(), anyFloat(), any());

        GeneratorContext context = mock(GeneratorContext.class);
        context.levels = levels;
        context.generator = mock(TileGenerator.class);
        when(context.generator.getHeightmap()).thenReturn(heightmap);
        context.cache = mock(TileCache.class, invocation -> {
            throw new AssertionError("Terrain sampling must not request cached tiles");
        });
        context.lookup = mock(WorldLookup.class, invocation -> {
            throw new AssertionError("Terrain sampling must not invoke WorldLookup");
        });
        provider = new RtfSampleProvider(context);
    }

    @Test
    void mapsNativeHeightAndClimateWithoutGeneratingTilesOrRivers() {
        ChunkPos pos = new ChunkPos(2, 3);

        Sample sample = provider.sampleCore(pos);

        assertEquals(pos, sample.pos());
        assertEquals(192, sample.terrainHeight());
        assertEquals(1.0, sample.continents(), 0.00001);
        assertFalse(sample.isOcean());
        assertTrue(sample.hasFullDensities());
        assertEquals(0.125, sample.erosion());
        assertEquals(0.625, sample.ridges());
        assertEquals(-0.75, sample.temperature());
        assertEquals(0.375, sample.vegetation());
    }

    @Test
    void recognizesBothNativeOceanTerrainTypes() {
        for (Terrain terrain : List.of(TerrainType.DEEP_OCEAN, TerrainType.SHALLOW_OCEAN)) {
            doAnswer(invocation -> {
                Cell cell = invocation.getArgument(0);
                cell.height = 0.2F;
                cell.continentEdge = 0.15F;
                cell.terrain = terrain;
                return null;
            }).when(heightmap).applyTerrain(any(Cell.class), anyFloat(), anyFloat());

            Sample sample = provider.sampleCore(new ChunkPos(1, 2));

            assertTrue(sample.isOcean());
            assertEquals(51, sample.terrainHeight());
        }
    }

    @Test
    void samplesTheMiddleOfChunksAtNegativeCoordinates() {
        ChunkPos pos = new ChunkPos(-1, -3);

        Sample sample = provider.sampleCore(pos);

        assertEquals(pos, sample.pos());
        assertEquals(List.of(-8.0F), terrainX);
        assertEquals(List.of(-40.0F), terrainZ);
    }

    @Test
    void isolatesMutableCellsAcrossQueriesAndKeepsReturnedSamplesImmutable() {
        ChunkPos firstPos = new ChunkPos(0, 0);
        Sample first = provider.sampleCore(firstPos);
        provider.sampleCore(new ChunkPos(1, 0));
        terrainCells.get(0).height = 0.0F;
        terrainCells.get(0).temperature = 0.0F;

        Sample repeated = provider.sampleCore(firstPos);

        assertNotSame(terrainCells.get(0), terrainCells.get(1));
        assertNotSame(terrainCells.get(0), terrainCells.get(2));
        assertEquals(192, first.terrainHeight());
        assertEquals(-0.75, first.temperature());
        assertEquals(first, repeated);
    }

    @Test
    void individualFieldQueriesUseTheSameNativeValues() {
        ChunkPos pos = new ChunkPos(2, 3);

        assertEquals(1.0, provider.sample(DensityField.CONTINENTS, pos), 0.00001);
        assertEquals(0.125, provider.sample(DensityField.EROSION, pos));
        assertEquals(0.625, provider.sample(DensityField.RIDGES, pos));
        assertEquals(-0.75, provider.sample(DensityField.TEMPERATURE, pos));
        assertEquals(0.375, provider.sample(DensityField.VEGETATION, pos));
    }

    @Test
    void recordsCoreSamplingCost() {
        Timer timer = new Timer();
        try (MockedStatic<Store> store = mockStatic(Store.class)) {
            store.when(() -> Store.getTimer(RtfSampleProvider.class, "sampleCore")).thenReturn(timer);

            provider.sampleCore(new ChunkPos(0, 0));

            assertEquals(1, timer.count());
        }
    }
}
