package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;

public class Seep implements FeatureHandler {

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        SourceConfig config = CommonConfig.get().sources();
        if (cell.hasInflowingNeighbor()) {
            return false;
        }
        if (cell.averageVegetation() <= config.seepMinVegetation()) {
            return false;
        }
        if (cell.averageErosion() >= config.seepMaxErosion()) {
            return false;
        }
        return true;
    }

    @Override
    public List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        if (!entries.isEmpty() || exit.isEmpty()) {
            return FeatureHandler.super.buildSplines(cell, entries, exit, meanderSeed);
        }
        SourceChannel channel = new SourceChannel(cell, exit.get(), meanderSeed);
        return List.of(channel.toExit(channel.origin(0.4, 0)));
    }
}
