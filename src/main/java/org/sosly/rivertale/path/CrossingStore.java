package org.sosly.rivertale.path;

import java.util.LinkedHashMap;
import java.util.Map;
import org.sosly.rivertale.core.RegionPos;

public class CrossingStore {

    private static final int DEFAULT_CAPACITY = 1000;
    private static final CrossingStore INSTANCE = new CrossingStore();

    private final Map<Long, Crossing> cache;

    private CrossingStore() {
        this.cache = new LinkedHashMap<>(DEFAULT_CAPACITY, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Crossing> eldest) {
                return size() > DEFAULT_CAPACITY;
            }
        };
    }

    public static CrossingStore getInstance() {
        return INSTANCE;
    }

    public Crossing getOrCreate(RegionPos source, RegionPos destination, int slot) {
        long key = packKey(source, destination);
        Crossing cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        Crossing crossing = new Crossing(source, destination, slot);
        cache.put(key, crossing);
        return crossing;
    }

    public void clear() {
        cache.clear();
    }

    private static long packKey(RegionPos a, RegionPos b) {
        boolean aFirst = (a.x() < b.x()) || (a.x() == b.x() && a.z() <= b.z());
        RegionPos first = aFirst ? a : b;
        RegionPos second = aFirst ? b : a;
        int packed1 = (first.x() << 16) | (first.z() & 0xFFFF);
        int packed2 = (second.x() << 16) | (second.z() & 0xFFFF);
        return ((long) packed1 << 32) | (packed2 & 0xFFFFFFFFL);
    }
}
