package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;

public class Snowmelt implements FeatureHandler {

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        SourceConfig config = CommonConfig.get().sources();
        if (cell.averageEstimatedTerrainHeight() < config.snowmeltMinHeight()) {
            return false;
        }
        if (cell.hasInflowingNeighbor()) {
            return false;
        }
        if (cell.averageTemperature() >= config.snowmeltMaxTemperature()) {
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
        RiverSpline main = channel.toExit(channel.origin(-0.6, 0));
        return List.of(channel.rill(main, -0.125, 0.5), channel.rill(main, 0.125, 0.5),
            t -> main.pointAt(0.5 + t * 0.5));
    }
}
