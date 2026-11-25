package org.sosly.rivertale.cell;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.metric.Store;

public class CellCache {

    private static volatile CellCache instance;

    private final Map<Long, Cell> cache;
    private final int capacity;

    private static final Map<Integer, List<SampleOffset>> SAMPLE_POSITION_CACHE = new ConcurrentHashMap<>();

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
        instance = new CellCache(CommonConfig.get().caches().cells());
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

        Set<ChunkPos> samplePositions = collectSamplePositions(pos);
        Cell cell = new Cell(pos, samplePositions);
        Cell existing = cache.putIfAbsent(key, cell);
        return existing == null ? cell : existing;
    }

    public Cell getOrComputeWithFlow(CellPos pos) {
        Cell cell = getOrCompute(pos);
        if (!cell.flowDirections().isEmpty()) {
            return cell;
        }

        if (cell.isOcean()) {
            return cell;
        }

        List<FlowDirection> flowDirections = computeFlowDirections(cell);
        return cache.compute(pos.toLong(), (key, cached) -> {
            Cell current = cached == null ? cell : cached;
            return current.withFlowDirections(flowDirections);
        });
    }

    public Cell setFeatureIfNone(Cell classified) {
        return cache.compute(classified.pos().toLong(), (key, cached) -> {
            if (cached == null) {
                return classified;
            }
            if (cached.feature() != Feature.NONE || classified.feature() == Feature.NONE) {
                return cached;
            }
            return cached.withFeature(classified.feature());
        });
    }

    public void clear() {
        cache.clear();
    }

    private Set<ChunkPos> collectSamplePositions(CellPos pos) {
        CommonConfig config = CommonConfig.get();
        List<SampleOffset> offsets = getSamplePositions(config.cellSize(), config.samplesPerCell());
        Set<ChunkPos> positions = new HashSet<>();

        int cellMinChunkX = pos.x() * config.cellSize();
        int cellMinChunkZ = pos.z() * config.cellSize();

        for (SampleOffset offset : offsets) {
            int chunkX = cellMinChunkX + offset.x();
            int chunkZ = cellMinChunkZ + offset.z();
            positions.add(new ChunkPos(chunkX, chunkZ));
        }

        return positions;
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
