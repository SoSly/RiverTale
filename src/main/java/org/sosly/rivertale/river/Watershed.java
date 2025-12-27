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
import org.sosly.rivertale.config.CommonConfig;
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

        assignYLevels();
        reclassify();

        record.stop();
    }

    private void addPath(Path path) {
        Timer.Record record = Store.getTimer(Watershed.class, "addPath").start();

        List<CellPos> cells = path.cells();

        for (int i = 0; i < cells.size() - 1; i++) {
            CellPos current = cells.get(i);
            CellPos next = cells.get(i + 1);

            CellPos crossingTarget = detectDiagonalCrossing(current, next);
            if (crossingTarget != null) {
                int currentAccumulation = i;
                int existingAccumulation = accumulation(crossingTarget);
                CellPos crossingDownstream = downstream.get(crossingTarget);

                if (currentAccumulation <= existingAccumulation) {
                    downstream.put(current, crossingDownstream);
                    upstream.computeIfAbsent(crossingDownstream, k -> new HashSet<>()).add(current);
                    record.stop();
                    return;
                } else {
                    pruneDownstream(crossingTarget);
                    downstream.put(crossingTarget, next);
                    upstream.computeIfAbsent(next, k -> new HashSet<>()).add(crossingTarget);
                }
            }

            downstream.put(current, next);
            upstream.computeIfAbsent(next, k -> new HashSet<>()).add(current);
        }

        if (!cells.isEmpty()) {
            CellPos last = cells.get(cells.size() - 1);
            downstream.put(last, null);
        }

        record.stop();
    }

    private CellPos detectDiagonalCrossing(CellPos current, CellPos next) {
        int dx = next.x() - current.x();
        int dz = next.z() - current.z();

        if (dx == 0 || dz == 0) {
            return null;
        }

        CellPos neighborX = new CellPos(current.x() + dx, current.z());
        CellPos neighborZ = new CellPos(current.x(), current.z() + dz);

        if (downstream.containsKey(neighborX) && downstream.get(neighborX) != null) {
            if (downstream.get(neighborX).equals(neighborZ)) {
                return neighborX;
            }
        }

        if (downstream.containsKey(neighborZ) && downstream.get(neighborZ) != null) {
            if (downstream.get(neighborZ).equals(neighborX)) {
                return neighborZ;
            }
        }

        return null;
    }

    private void pruneDownstream(CellPos start) {
        CellPos current = downstream.get(start);

        while (current != null) {
            Set<CellPos> upstreamSet = upstream.get(current);
            if (upstreamSet != null && upstreamSet.size() > 1) {
                upstreamSet.remove(start);
                break;
            }

            CellPos next = downstream.get(current);
            downstream.remove(current);
            upstream.remove(current);
            upstreamCountCache.remove(current);

            start = current;
            current = next;
        }
    }

    private void assignYLevels() {
        Timer.Record record = Store.getTimer(Watershed.class, "assignYLevels").start();

        CellCache cache = CellCache.get();
        int minSlope = CommonConfig.get().minSlope();
        Set<CellPos> visited = new HashSet<>();

        for (CellPos cellPos : allCells()) {
            if (!upstream.containsKey(cellPos) || upstream.get(cellPos).isEmpty()) {
                assignYLevelsFromSource(cellPos, cache, minSlope, visited);
            }
        }

        record.stop();
    }

    private void assignYLevelsFromSource(CellPos source, CellCache cache, int minSlope, Set<CellPos> visited) {
        CellPos current = source;

        while (current != null && !visited.contains(current)) {
            visited.add(current);

            Cell cell = cache.getOrCompute(current);
            int terrainY = cell.sample().estimatedHeight();

            Set<CellPos> upstreamCells = upstream.get(current);
            if (upstreamCells == null || upstreamCells.isEmpty()) {
                cache.put(cell.withY(terrainY));
            } else {
                int maxAllowedY = Integer.MAX_VALUE;
                for (CellPos upstreamPos : upstreamCells) {
                    Cell upstreamCell = cache.getOrCompute(upstreamPos);
                    maxAllowedY = Math.min(maxAllowedY, upstreamCell.y() - minSlope);
                }
                int constrainedY = Math.min(terrainY, maxAllowedY);
                cache.put(cell.withY(constrainedY));
            }

            current = downstream.get(current);
        }
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

    public ListTag encode(CellCache cellCache) {
        Timer.Record record = Store.getTimer(Watershed.class, "encode").start();

        ListTag edges = new ListTag();
        for (Map.Entry<CellPos, CellPos> entry : downstream.entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }

            Cell fromCell = cellCache.getOrCompute(entry.getKey());
            Cell toCell = cellCache.getOrCompute(entry.getValue());

            CompoundTag edge = new CompoundTag();
            edge.putLong("from", entry.getKey().toLong());
            edge.putLong("to", entry.getValue().toLong());
            edge.putInt("fromY", fromCell.y());
            edge.putInt("toY", toCell.y());
            edges.add(edge);
        }

        record.stop();
        return edges;
    }
}
