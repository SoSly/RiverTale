package org.sosly.rivertale.river;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public final class WatershedPreparation {
    private final FlowlinePreparation tracing;
    private final UpstreamSearch search;
    private final Set<Flowline> indexed = new HashSet<>();
    private final Set<Flowline> included = new HashSet<>();
    private final ArrayDeque<Flowline> pending = new ArrayDeque<>();
    private final Set<CellPos> cells = new HashSet<>();
    private final Set<CellPos> crossings = new HashSet<>();
    private final Map<CellPos, Set<Flowline>> cellPaths = new HashMap<>();
    private final Map<CellPos, Set<Flowline>> crossingPaths = new HashMap<>();

    private WatershedPreparation(FlowlinePreparation.Result prepared) {
        tracing = new FlowlinePreparation(prepared);
        search = new UpstreamSearch(prepared.tracer());
    }

    public static List<Flowline> gather(FlowlinePreparation.Result prepared) {
        Timer.Record timer = Store.getTimer(WatershedPreparation.class, "gather").start();
        try {
            WatershedPreparation preparation = new WatershedPreparation(prepared);
            prepared.upstream().values().forEach(paths -> paths.forEach(preparation::include));
            preparation.discover();
            Store.getDistribution(WatershedPreparation.class, "examinedRegions").record(preparation.search.regionCount());
            Store.getDistribution(WatershedPreparation.class, "upstreamCells").record(preparation.search.cellCount());
            Store.getDistribution(WatershedPreparation.class, "paths").record(preparation.included.size());
            return List.copyOf(preparation.included);
        } finally {
            timer.stop();
        }
    }

    private void include(Flowline path) {
        if (path.isValid() && !path.cells().isEmpty() && included.add(path)) {
            pending.add(path);
        }
    }

    private void discover() {
        while (!pending.isEmpty()) {
            Flowline path = pending.remove();
            for (CellPos cell : occupiedCells(path)) {
                cells.add(cell);
                inspect(cell);
                cellPaths.getOrDefault(cell, Set.of()).forEach(this::include);
            }
            for (CellPos square : diagonalSquares(path)) {
                crossings.add(square);
                inspect(square);
                inspect(new CellPos(square.x() + 1, square.z()));
                inspect(new CellPos(square.x(), square.z() + 1));
                inspect(new CellPos(square.x() + 1, square.z() + 1));
                crossingPaths.getOrDefault(square, Set.of()).forEach(this::include);
            }
        }
    }

    private void inspect(CellPos target) {
        for (RegionPos source : search.expand(List.of(target))) {
            tracing.paths(source).forEach(this::index);
        }
    }

    private void index(Flowline path) {
        if (!path.isValid() || !indexed.add(path)) {
            return;
        }
        boolean connected = indexPositions(path, occupiedCells(path), cells, cellPaths);
        connected |= indexPositions(path, diagonalSquares(path), crossings, crossingPaths);
        if (connected) {
            include(path);
        }
    }

    private boolean indexPositions(Flowline path, Collection<CellPos> positions, Set<CellPos> selected,
                                   Map<CellPos, Set<Flowline>> index) {
        boolean connected = false;
        for (CellPos pos : positions) {
            index.computeIfAbsent(pos, key -> new HashSet<>()).add(path);
            connected |= selected.contains(pos);
        }
        return connected;
    }

    private Collection<CellPos> occupiedCells(Flowline path) {
        if (path.lakes().isEmpty()) {
            return path.cells();
        }
        Set<CellPos> positions = new HashSet<>(path.cells());
        path.lakes().forEach(lake -> positions.addAll(lake.depths().keySet()));
        return positions;
    }

    private Set<CellPos> diagonalSquares(Flowline path) {
        Set<CellPos> squares = new HashSet<>();
        for (int i = 1; i < path.cells().size(); i++) {
            CellPos from = path.cells().get(i - 1);
            CellPos to = path.cells().get(i);
            if (Math.abs(from.x() - to.x()) == 1 && Math.abs(from.z() - to.z()) == 1) {
                squares.add(new CellPos(Math.min(from.x(), to.x()), Math.min(from.z(), to.z())));
            }
        }
        return squares;
    }
}
