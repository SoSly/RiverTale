package org.sosly.rivertale.terrain;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.DensityField;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;
import org.sosly.rivertale.metric.Store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CoastalTest {

    @Mock
    CellCache cellCache;

    private TestSampleProvider provider;

    @BeforeEach
    void setUp() {
        Store.clear();
        CommonConfig.set(new CommonConfig(32, 2, 1, false, false, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        SampleCache.shutdown();
        provider = new TestSampleProvider();
        SampleCache.init(provider);
    }

    @AfterEach
    void tearDown() {
        SampleCache.shutdown();
    }

    @Test
    void emptyWhenAllOcean() {
        RegionPos region = new RegionPos(0, 0);
        mockCellCache(pos -> createOceanCell(pos));

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        assertTrue(boundaries.isEmpty());
    }

    @Test
    void emptyWhenAllLandWithNoWaterNeighbors() {
        RegionPos region = new RegionPos(0, 0);
        mockCellCache(pos -> createLandCell(pos));

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        assertTrue(boundaries.isEmpty());
    }

    @Test
    void findsOceanBoundary() {
        int boundaryCellX = 16;
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        mockCellCache(pos -> {
            if (pos.x() >= minCell.x() + boundaryCellX) {
                return createOceanCell(pos);
            }
            return createLandCell(pos);
        });

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        assertEquals(oceanEdgesAlongColumn(minCell, boundaryCellX), Set.copyOf(boundaries));
        assertEquals(CommonConfig.get().cellsPerRegion(), boundaries.size());
    }

    @Test
    void findsBasinBoundary() {
        RegionPos region = new RegionPos(0, 0);
        CellPos basinPos = new CellPos(region.getMinCell().x() + 10, region.getMinCell().z() + 10);
        mockCellCache(pos -> {
            if (pos.equals(basinPos)) {
                return createBasinCell(pos);
            }
            return createLandCell(pos);
        });

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        assertEquals(basinEdges(basinPos), Set.copyOf(boundaries));
        assertEquals(4, boundaries.size());
    }

    @Test
    void findsBothBoundaryTypes() {
        int boundaryCellX = 20;
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        CellPos basinPos = new CellPos(minCell.x() + 10, minCell.z() + 10);
        mockCellCache(pos -> {
            if (pos.x() >= minCell.x() + boundaryCellX) {
                return createOceanCell(pos);
            }
            if (pos.equals(basinPos)) {
                return createBasinCell(pos);
            }
            return createLandCell(pos);
        });

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        Set<Boundary> expected = new HashSet<>(oceanEdgesAlongColumn(minCell, boundaryCellX));
        expected.addAll(basinEdges(basinPos));
        assertEquals(expected, Set.copyOf(boundaries));
        assertEquals(CommonConfig.get().cellsPerRegion() + 4, boundaries.size());
    }

    @Test
    void multipleFacesPerCell() {
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        CellPos peninsulaCell = new CellPos(minCell.x() + 10, minCell.z() + 10);
        mockCellCache(pos -> {
            if (pos.equals(peninsulaCell)) {
                return createLandCell(pos);
            }
            return createOceanCell(pos);
        });

        List<Boundary> boundaries = Coastal.boundaries(region, cellCache);

        long boundariesForPeninsula = boundaries.stream()
            .filter(b -> b.land().equals(peninsulaCell))
            .count();
        assertEquals(4, boundariesForPeninsula);
    }

    /** Land cells in the column just west of the ocean edge, one ocean boundary per row. */
    private static Set<Boundary> oceanEdgesAlongColumn(CellPos minCell, int oceanStartOffset) {
        Set<Boundary> expected = new HashSet<>();
        for (int z = 0; z < CommonConfig.get().cellsPerRegion(); z++) {
            expected.add(new Boundary(
                new CellPos(minCell.x() + oceanStartOffset - 1, minCell.z() + z),
                new CellPos(minCell.x() + oceanStartOffset, minCell.z() + z),
                BoundaryType.OCEAN));
        }
        return expected;
    }

    private static Set<Boundary> basinEdges(CellPos basin) {
        Set<Boundary> expected = new HashSet<>();
        for (FlowDirection dir : FlowDirection.D4) {
            expected.add(new Boundary(basin.relative(dir), basin, BoundaryType.BASIN));
        }
        return expected;
    }

    private void mockCellCache(Function<CellPos, Cell> cellFactory) {
        when(cellCache.getOrCompute(any(CellPos.class))).thenAnswer(invocation -> {
            CellPos pos = invocation.getArgument(0);
            return cellFactory.apply(pos);
        });
    }

    /** The chunk CellCache would sample for this cell at cellSize=2, samplesPerCell=1 (the cell's center chunk). */
    private static ChunkPos sampleChunk(CellPos pos) {
        int cellSize = CommonConfig.get().cellSize();
        return new ChunkPos(pos.x() * cellSize + cellSize / 2, pos.z() * cellSize + cellSize / 2);
    }

    private Cell createOceanCell(CellPos pos) {
        ChunkPos chunk = sampleChunk(pos);
        Sample sample = new Sample(chunk, -0.5, 142, true);
        provider.putSample(chunk, sample);
        return new Cell(pos, Set.of(chunk));
    }

    private Cell createLandCell(CellPos pos) {
        ChunkPos chunk = sampleChunk(pos);
        Sample sample = new Sample(chunk, 0.5, 142, false);
        provider.putSample(chunk, sample);
        return new Cell(pos, Set.of(chunk));
    }

    private Cell createBasinCell(CellPos pos) {
        ChunkPos chunk = sampleChunk(pos);
        Sample sample = new Sample(chunk, 0.5, 56, false);
        provider.putSample(chunk, sample);
        return new Cell(pos, Set.of(chunk));
    }

    private static class TestSampleProvider implements SampleProvider {
        private final java.util.Map<Long, Sample> samples = new java.util.HashMap<>();

        void putSample(ChunkPos pos, Sample sample) {
            samples.put(pos.toLong(), sample);
            if (SampleCache.isInitialized()) {
                SampleCache.get().put(pos, sample);
            }
        }

        @Override
        public Sample sampleCore(ChunkPos pos) {
            Sample cached = samples.get(pos.toLong());
            if (cached != null) {
                return cached;
            }
            return new Sample(pos, 0.0, 70, false);
        }

        @Override
        public double sample(DensityField field, ChunkPos pos) {
            return 0.0;
        }
    }
}
