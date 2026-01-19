package org.sosly.rivertale.cell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Cache;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.region.RegionTypeCache;

public class CellCache implements Cache<Cell> {
    private static final int DEFAULT_CAPACITY = 50_000;

    private static volatile CellCache instance;

    private final Map<Long, Cell> cache;
    private final int capacity;

    private static final Map<Integer, List<SampleOffset>> SAMPLE_POSITION_CACHE = new HashMap<>();

    record SampleOffset(int x, int z) {}
    private record SlopeEntry(FlowDirection dir, double slope) {}

    private CellCache(int capacity) {
        this.capacity = capacity;
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Cell> eldest) {
                return size() > CellCache.this.capacity;
            }
        });
    }

    public static synchronized void init() {
        if (instance != null) {
            return;
        }
        instance = new CellCache(DEFAULT_CAPACITY);
    }

    public static CellCache get() {
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.clear();
            instance = null;
        }
    }

    @Override
    public Cell getOrCompute(int x, int z) {
        return getOrCompute(new CellPos(x, z));
    }

    public Cell getIfPresent(CellPos pos) {
        return cache.get(pos.toLong());
    }

    public Cell getOrCompute(CellPos pos) {
        long key = pos.toLong();
        Cell cached = cache.get(key);

        if (cached != null) {
            Store.getRatio(CellCache.class, "hits").success();
            return cached;
        }

        Store.getRatio(CellCache.class, "hits").failure();

        Map<ChunkPos, Sample> samples = collectSamples(pos);
        Cell cell = new Cell(pos, samples);
        cache.put(key, cell);

        return cell;
    }

    public Cell getOrComputeWithFlow(CellPos pos) {
        Cell cell = getOrCompute(pos);
        if (!cell.flowDirections().isEmpty()) {
            return cell;
        }

        SampleCache sampleCache = SampleCache.get();
        RegionType regionType = RegionTypeCache.get().getOrCompute(pos.getRegion(), sampleCache);
        if (regionType == RegionType.OCEANIC || regionType == RegionType.INLAND) {
            return cell;
        }

        List<FlowDirection> flowDirections = computeFlowDirections(cell);
        Cell enriched = cell.withFlowDirections(flowDirections);
        put(enriched);
        return enriched;
    }

    public void put(Cell cell) {
        cache.put(cell.pos().toLong(), cell);
    }

    @Override
    public void clear() {
        cache.clear();
    }

    private Map<ChunkPos, Sample> collectSamples(CellPos pos) {
        CommonConfig config = CommonConfig.get();
        List<SampleOffset> offsets = getSamplePositions(config.cellSize(), config.samplesPerCell());
        Map<ChunkPos, Sample> samples = new HashMap<>();

        int cellMinChunkX = pos.x() * config.cellSize();
        int cellMinChunkZ = pos.z() * config.cellSize();

        SampleCache sampleCache = SampleCache.get();
        for (SampleOffset offset : offsets) {
            int chunkX = cellMinChunkX + offset.x();
            int chunkZ = cellMinChunkZ + offset.z();
            ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
            Sample sample = sampleCache.getOrCompute(chunkX * 16, chunkZ * 16);
            samples.put(chunkPos, sample);
        }

        return samples;
    }

    static List<SampleOffset> getSamplePositions(int cellSize, int samplesPerCell) {
        int key = cellSize * 100 + samplesPerCell;
        return SAMPLE_POSITION_CACHE.computeIfAbsent(key, k -> selectSamplePositions(cellSize, samplesPerCell));
    }

    private static List<SampleOffset> selectSamplePositions(int cellSize, int samplesPerCell) {
        int center = cellSize / 2;
        List<SampleOffset> selected = new ArrayList<>();
        selected.add(new SampleOffset(center, center));

        while (selected.size() < samplesPerCell) {
            SampleOffset bestPos = null;
            double bestMinDist = -1;

            for (int x = 0; x < cellSize; x++) {
                for (int z = 0; z < cellSize; z++) {
                    SampleOffset candidate = new SampleOffset(x, z);
                    if (containsOffset(selected, candidate)) {
                        continue;
                    }

                    double minDist = Double.MAX_VALUE;
                    for (SampleOffset s : selected) {
                        double dist = distance(candidate, s);
                        if (dist < minDist) {
                            minDist = dist;
                        }
                    }

                    if (minDist > bestMinDist) {
                        bestMinDist = minDist;
                        bestPos = candidate;
                    }
                }
            }

            if (bestPos == null) {
                break;
            }
            selected.add(bestPos);
        }

        return selected;
    }

    private static boolean containsOffset(List<SampleOffset> list, SampleOffset offset) {
        for (SampleOffset s : list) {
            if (s.x() == offset.x() && s.z() == offset.z()) {
                return true;
            }
        }
        return false;
    }

    private static double distance(SampleOffset a, SampleOffset b) {
        int dx = a.x() - b.x();
        int dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private List<FlowDirection> computeFlowDirections(Cell cell) {
        int thisHeight = cell.averageEstimatedTerrainHeight();
        List<SlopeEntry> slopes = new ArrayList<>();

        for (FlowDirection dir : FlowDirection.D8) {
            Cell neighbor = getOrCompute(cell.pos().relative(dir));
            int neighborHeight = neighbor.averageEstimatedTerrainHeight();

            if (neighborHeight < thisHeight) {
                double slope = thisHeight - neighborHeight;
                slopes.add(new SlopeEntry(dir, slope));
            }
        }

        slopes.sort(Comparator.comparingDouble(SlopeEntry::slope).reversed());
        return slopes.stream().map(SlopeEntry::dir).toList();
    }

}
