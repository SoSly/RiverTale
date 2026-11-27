package org.sosly.rivertale.cell.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.CarveProposal;

interface SourceFeature extends FeatureHandler {
    @Override
    default List<CarveProposal> carve(Cell cell, Watershed watershed, ChunkPos chunk, long seed) {
        OptionalDouble flow = watershed.flow(cell.pos());
        if (cell.cellY().isEmpty() || flow.isEmpty()) {
            return List.of();
        }
        List<Cell> entries = watershed.upstream(cell.pos()).stream().sorted((a, b) -> Long.compare(a.toLong(), b.toLong()))
            .map(watershed::elevatedCell).toList();
        Optional<Cell> exit = Optional.ofNullable(watershed.downstream(cell.pos())).map(watershed::elevatedCell);
        List<RiverSpline> paths = buildSplines(cell, entries, exit, seed);
        int size = CommonConfig.get().cellBlocks();
        if (entries.isEmpty() && exit.isPresent()) {
            return carveHeadwater(paths, flow.getAsDouble(), chunk, size);
        }
        List<CarveProposal> proposals = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            double discharge = i < entries.size() ? watershed.flow(entries.get(i).pos()).orElseThrow() : flow.getAsDouble();
            proposals.addAll(CourseCarver.carve(List.of(paths.get(i)), discharge, discharge, chunk, size));
        }
        return proposals;
    }

    default List<CarveProposal> carveHeadwater(List<RiverSpline> paths, double flow, ChunkPos chunk, int size) {
        return CourseCarver.carve(paths, flow, flow, chunk, size);
    }
}
