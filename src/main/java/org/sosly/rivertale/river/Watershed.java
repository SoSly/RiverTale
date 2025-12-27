package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import java.util.Optional;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public class Watershed {
    private final RegionPos pos;
    private final Map<CellPos, CellPos> downstream = new HashMap<>();
    private final Map<CellPos, Set<CellPos>> upstream = new HashMap<>();
    private final Map<CellPos, Integer> upstreamCountCache = new HashMap<>();

    public Watershed(RegionPos pos, List<Path> paths) {
        Timer.Record record = Store.getTimer(Watershed.class, "constructor").start();

        this.pos = pos;

        for (Path path : paths) {
            addPath(path);
        }

        reclassify();

        record.stop();
    }

    private void addPath(Path path) {
        Timer.Record record = Store.getTimer(Watershed.class, "addPath").start();

        List<CellPos> cells = path.cells();

        for (int i = 0; i < cells.size() - 1; i++) {
            CellPos current = cells.get(i);
            CellPos next = cells.get(i + 1);

            downstream.put(current, next);
            upstream.computeIfAbsent(next, k -> new HashSet<>()).add(current);
        }

        if (!cells.isEmpty()) {
            CellPos last = cells.get(cells.size() - 1);
            downstream.put(last, null);
        }

        record.stop();
    }

    private void reclassify() {
        Timer.Record record = Store.getTimer(Watershed.class, "reclassify").start();

        CellCache cache = CellCache.get();
        for (CellPos pos : allCells()) {
            Cell current = cache.getOrCompute(pos);
            Cell reclassified = Feature.classify(current, this);
            cache.put(reclassified);
        }

        record.stop();
    }

    public Set<CellPos> upstream(CellPos cell) {
        return upstream.getOrDefault(cell, Set.of());
    }

    public CellPos downstream(CellPos cell) {
        return downstream.get(cell);
    }

    public int accumulation(CellPos cell) {
        if (!downstream.containsKey(cell)) {
            return 0;
        }

        Integer cached = upstreamCountCache.get(cell);
        if (cached != null) {
            return cached;
        }

        int count = 0;
        for (CellPos tributary : upstream(cell)) {
            count += 1 + accumulation(tributary);
        }

        upstreamCountCache.put(cell, count);
        return count;
    }

    public Set<CellPos> allCells() {
        return downstream.keySet();
    }

    public boolean contains(CellPos cell) {
        return downstream.containsKey(cell);
    }

    public RegionPos pos() {
        return pos;
    }

    public Optional<CellPos> terminus(CellPos cell) {
        if (!downstream.containsKey(cell)) {
            return Optional.empty();
        }

        CellPos current = cell;
        while (downstream.get(current) != null) {
            current = downstream.get(current);
        }
        return Optional.of(current);
    }

    public ListTag encode() {
        Timer.Record record = Store.getTimer(Watershed.class, "encode").start();

        ListTag edges = new ListTag();
        for (Map.Entry<CellPos, CellPos> entry : downstream.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            CompoundTag edge = new CompoundTag();
            edge.putLong("from", entry.getKey().toLong());
            edge.putLong("to", entry.getValue().toLong());
            edges.add(edge);
        }

        record.stop();
        return edges;
    }
}
