package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public class Watershed {
    private final RegionPos terminus;
    private final Map<CellPos, CellPos> downstream = new HashMap<>();
    private final Map<CellPos, Set<CellPos>> upstream = new HashMap<>();
    private final Map<CellPos, Integer> upstreamCountCache = new HashMap<>();

    public Watershed(RegionPos terminus, List<Path> paths) {
        Timer.Record record = Store.getTimer(Watershed.class, "constructor").start();

        this.terminus = terminus;

        for (Path path : paths) {
            addPath(path);
        }

        record.stop();
    }

    private void addPath(Path path) {
        Timer.Record record = Store.getTimer(Watershed.class, "addPath").start();

        List<Cell> cells = path.cells();

        for (int i = 0; i < cells.size() - 1; i++) {
            CellPos current = cells.get(i).pos();
            CellPos next = cells.get(i + 1).pos();

            downstream.put(current, next);
            upstream.computeIfAbsent(next, k -> new HashSet<>()).add(current);
        }

        if (!cells.isEmpty()) {
            CellPos last = cells.get(cells.size() - 1).pos();
            downstream.put(last, null);
        }

        record.stop();
    }

    public Set<CellPos> tributaries(CellPos cell) {
        return upstream.getOrDefault(cell, Set.of());
    }

    public int upstream(CellPos cell) {
        if (!downstream.containsKey(cell)) {
            return 0;
        }

        Integer cached = upstreamCountCache.get(cell);
        if (cached != null) {
            return cached;
        }

        int count = 0;
        for (CellPos tributary : tributaries(cell)) {
            count += 1 + upstream(tributary);
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

    public RegionPos terminus() {
        return terminus;
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
