package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;

public class Spring implements FeatureHandler {

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        CommonConfig config = CommonConfig.get();
        SourceConfig sources = config.sources();
        if (cell.flowDirections().isEmpty() || !isLand(cell)) {
            return false;
        }
        if (cell.averageRidges() >= sources.springMaxRidges() || cell.hasInflowingNeighbor()) {
            return false;
        }

        double height = cell.averageEstimatedTerrainHeight();
        for (FlowDirection direction : cell.flowDirections()) {
            if (direction == FlowDirection.NONE) {
                continue;
            }
            Cell neighbor = CellCache.get().getOrCompute(cell.pos().relative(direction));
            if (!isLand(neighbor)) {
                continue;
            }
            double drop = height - neighbor.averageEstimatedTerrainHeight();
            double distance = config.cellBlocks() * Math.hypot(direction.dx, direction.dz);
            if (drop > 0 && drop / distance >= sources.springMinGrade()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        if (!entries.isEmpty() || exit.isEmpty()) {
            return FeatureHandler.super.buildSplines(cell, entries, exit, meanderSeed);
        }
        SourceChannel channel = new SourceChannel(cell, exit.get(), meanderSeed);
        return List.of(channel.toExit(channel.origin(-0.25, 0.125)));
    }

    private boolean isLand(Cell cell) {
        return !cell.isOcean() && !cell.isBasin();
    }
}
