package org.sosly.rivertale.cell;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.Cache;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.region.RegionType;

public class CellCache implements Cache<Cell> {
    private static final int DEFAULT_CAPACITY = 50_000;

    private static volatile CellCache instance;

    private final Map<Long, Cell> cache;
    private final int capacity;

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

    public Cell getOrCompute(CellPos pos) {
        long key = pos.toLong();
        Cell cached = cache.get(key);

        if (cached != null) {
            Store.getRatio(CellCache.class, "hits").success();
            return cached;
        }

        Store.getRatio(CellCache.class, "hits").failure();

        SampleCache sampleCache = SampleCache.get();
        Sample sample = sampleCache.getOrCompute(pos.getMiddleBlockX(), pos.getMiddleBlockZ());

        RegionType regionType = RegionType.classify(pos.getRegion(), sampleCache);
        if (regionType == RegionType.OCEANIC || regionType == RegionType.INLAND) {
            Cell value = new Cell(pos, sample, Feature.DEFAULT, Direction.NONE);
            cache.put(key, value);
            return value;
        }

        Direction flowDirection = Cell.computeFlowDirection(pos, sample, sampleCache);
        Cell value = Feature.classify(new Cell(pos, sample, Feature.DEFAULT, flowDirection));
        cache.put(key, value);

        return value;
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
