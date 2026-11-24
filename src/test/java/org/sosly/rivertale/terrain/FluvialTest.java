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
class FluvialTest {

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
    void emptyWhenNoBasins() {
        RegionPos region = new RegionPos(0, 0);
        mockCellCache(pos -> createLandCell(pos));

        List<Boundary> boundaries = Fluvial.boundaries(region, cellCache);

        assertTrue(boundaries.isEmpty());
    }

    @Test
    void singleBasinYieldsExactlyTheFourLandToBasinEdges() {
        RegionPos region = new RegionPos(0, 0);
        CellPos basinPos = new CellPos(region.getMinCell().x() + 10, region.getMinCell().z() + 10);
        mockCellCache(pos -> pos.equals(basinPos) ? createBasinCell(pos) : createLandCell(pos));

        List<Boundary> boundaries = Fluvial.boundaries(region, cellCache);

        assertEquals(expectedBasinEdges(basinPos), Set.copyOf(boundaries));
        assertEquals(4, boundaries.size());
    }

    @Test
    void oceanCellsNeverProduceBoundariesButBasinsStillDo() {
        RegionPos region = new RegionPos(0, 0);
        CellPos minCell = region.getMinCell();
        CellPos basinPos = new CellPos(minCell.x() + 10, minCell.z() + 10);
        mockCellCache(pos -> {
            if (pos.x() >= minCell.x() + 16) {
                return createOceanCell(pos);
            }
            if (pos.equals(basinPos)) {
                return createBasinCell(pos);
            }
            return createLandCell(pos);
        });

        List<Boundary> boundaries = Fluvial.boundaries(region, cellCache);

        // Land cells touching the ocean (x = 15 -> 16) must not add anything; only the basin's edges remain.
        assertEquals(expectedBasinEdges(basinPos), Set.copyOf(boundaries));
        assertEquals(4, boundaries.size());
    }

    private static Set<Boundary> expectedBasinEdges(CellPos basin) {
        Set<Boundary> expected = new HashSet<>();
        for (FlowDirection dir : FlowDirection.D4) {
            // The land cell sits on the opposite side of the basin from the direction it faces.
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
