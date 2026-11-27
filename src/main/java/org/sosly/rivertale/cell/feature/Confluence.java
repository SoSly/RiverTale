package org.sosly.rivertale.cell.feature;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.CarveProposal;

public class Confluence implements FeatureHandler {
    private static final double MERGE = 0.5;

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return watershed != null && watershed.upstream(cell.pos()).size() > 1;
    }

    @Override
    public List<CarveProposal> carve(Cell cell, Watershed watershed, ChunkPos chunk, long meanderSeed) {
        OptionalDouble outflow = watershed.flow(cell.pos());
        if (cell.cellY().isEmpty() || outflow.isEmpty()) {
            return List.of();
        }
        Optional<Cell> exit = Optional.ofNullable(watershed.downstream(cell.pos())).map(watershed::elevatedCell);
        List<Cell> entries = watershed.upstream(cell.pos()).stream().map(watershed::elevatedCell).toList();
        List<Cell> ranked = rankEntries(cell, entries, exit);
        List<RiverSpline> paths = buildRankedSplines(cell, ranked, exit, meanderSeed);
        List<CarveProposal> proposals = new ArrayList<>();
        int cellBlocks = CommonConfig.get().cellBlocks();
        for (int i = 0; i < paths.size(); i++) {
            double flow = i < ranked.size() ? watershed.flow(ranked.get(i).pos()).orElseThrow() : outflow.getAsDouble();
            proposals.addAll(CourseCarver.carve(List.of(paths.get(i)), flow, flow, chunk, cellBlocks));
        }
        return proposals;
    }

    @Override
    public List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        return buildRankedSplines(cell, rankEntries(cell, entries, exit), exit, meanderSeed);
    }

    private List<Cell> rankEntries(Cell cell, List<Cell> entries, Optional<Cell> exit) {
        Vec3 downstream = exit.map(next -> direction(cell, next)).orElse(Vec3.ZERO);
        return entries.stream().sorted(Comparator
            .comparingDouble((Cell entry) -> direction(entry, cell).dot(downstream)).reversed()
            .thenComparingLong(entry -> entry.pos().toLong())).toList();
    }

    private List<RiverSpline> buildRankedSplines(Cell cell, List<Cell> ranked, Optional<Cell> exit, long meanderSeed) {
        if (ranked.size() < 2 || exit.isEmpty()) {
            return FeatureHandler.super.buildSplines(cell, ranked, exit, meanderSeed);
        }
        RiverSpline main = new RunSpline(cell, Optional.of(ranked.get(0)), exit, meanderSeed);
        Vec3 merge = main.pointAt(MERGE);
        Vec3 tangent = main.pointAt(MERGE + 0.001).subtract(main.pointAt(MERGE - 0.001)).normalize();
        List<RiverSpline> paths = new ArrayList<>();
        paths.add(t -> main.pointAt(t * MERGE));
        for (Cell entry : ranked.subList(1, ranked.size())) {
            paths.add(tributary(entry, cell, merge, tangent));
        }
        paths.add(t -> main.pointAt(MERGE + t * (1 - MERGE)));
        return List.copyOf(paths);
    }

    private static RiverSpline tributary(Cell entry, Cell cell, Vec3 merge, Vec3 tangent) {
        Vec3 start = CellCrossings.between(entry, cell);
        Vec3 incoming = direction(entry, cell);
        Vec3 first = start.add(incoming);
        double length = Math.hypot(merge.x - first.x, merge.z - first.z);
        double handle = length / 3;
        Vec3 control1 = first.add(incoming.scale(handle));
        double exitHandle = tangent.y < 0 ? Math.min(handle, (first.y - merge.y) / -tangent.y) : handle;
        Vec3 control2 = merge.subtract(tangent.scale(exitHandle));
        double levelFraction = 1 / (1 + length);
        return t -> {
            if (t <= levelFraction) {
                return start.lerp(first, t / levelFraction);
            }
            if (t == 1) {
                return merge;
            }
            double u = (t - levelFraction) / (1 - levelFraction);
            Vec3 middle = control1.lerp(control2, u);
            Vec3 upstream = first.lerp(control1, u).lerp(middle, u);
            Vec3 downstream = middle.lerp(control2.lerp(merge, u), u);
            return upstream.lerp(downstream, u);
        };
    }

    private static Vec3 direction(Cell from, Cell to) {
        return new Vec3(to.pos().x() - from.pos().x(), 0, to.pos().z() - from.pos().z()).normalize();
    }
}
