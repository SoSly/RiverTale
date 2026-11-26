package org.sosly.rivertale.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.RiverSpline;

record WatershedPreview(List<List<Vec3>> paths) {
    static WatershedPreview build(Collection<ClientRegionCache.Region> regions, long seed) {
        Map<CellPos, ClientRegionCache.Edge> edges = new HashMap<>();
        Map<CellPos, Feature> features = new HashMap<>();
        for (ClientRegionCache.Region region : regions) {
            region.edges().forEach(edge -> edges.put(edge.from(), edge));
            region.cells().forEach(cell -> features.put(cell.pos(), cell.feature()));
        }
        Map<CellPos, Cell> cells = new HashMap<>();
        Map<CellPos, List<Cell>> entries = new HashMap<>();
        Map<CellPos, Cell> exits = new HashMap<>();
        for (ClientRegionCache.Edge edge : edges.values()) {
            Cell from = cells.computeIfAbsent(edge.from(), pos -> cell(pos, edge.fromY(), features));
            Cell to = cells.computeIfAbsent(edge.to(), pos -> cell(pos, edge.toY(), features));
            entries.computeIfAbsent(to.pos(), pos -> new ArrayList<>()).add(from);
            exits.put(from.pos(), to);
        }
        entries.values().forEach(branches -> branches.sort(Comparator.comparingLong(cell -> cell.pos().toLong())));
        List<List<Vec3>> paths = new ArrayList<>();
        for (Cell cell : cells.values()) {
            List<Cell> incoming = entries.getOrDefault(cell.pos(), List.of());
            Optional<Cell> exit = Optional.ofNullable(exits.get(cell.pos()));
            for (RiverSpline spline : cell.feature().handler.buildSplines(cell, incoming, exit, seed)) {
                paths.add(sample(spline));
            }
        }
        return new WatershedPreview(paths);
    }

    private static Cell cell(CellPos pos, int y, Map<CellPos, Feature> features) {
        return new Cell(pos, Set.of(), List.of(), features.getOrDefault(pos, Feature.NONE), OptionalInt.of(y));
    }

    private static List<Vec3> sample(RiverSpline spline) {
        Vec3 start = spline.pointAt(0);
        Vec3 end = spline.pointAt(1);
        double length = start.distanceTo(end);
        int steps = Math.max(2, (int) Math.ceil(length / 4));
        double nearJoin = 0.5 / Math.max(2, length * 2);
        List<Vec3> points = new ArrayList<>(steps + 3);
        points.add(start);
        points.add(spline.pointAt(nearJoin));
        for (int i = 1; i < steps; i++) {
            points.add(spline.pointAt((double) i / steps));
        }
        points.add(spline.pointAt(1 - nearJoin));
        points.add(end);
        return points;
    }
}
