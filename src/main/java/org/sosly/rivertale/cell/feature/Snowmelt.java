package org.sosly.rivertale.cell.feature;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.CarveProposal;

public class Snowmelt implements SourceFeature {

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
    public List<CarveProposal> carveHeadwater(List<RiverSpline> paths, double flow, ChunkPos chunk, int size) {
        List<CarveProposal> proposals = new ArrayList<>();
        for (int i = 0; i < paths.size(); i++) {
            double discharge = i < paths.size() - 1 ? flow / 2 : flow;
            proposals.addAll(CourseCarver.carve(List.of(paths.get(i)), discharge, discharge, chunk, size));
        }
        return proposals;
    }

    @Override
    public List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        if (!entries.isEmpty() || exit.isEmpty()) {
            return SourceFeature.super.buildSplines(cell, entries, exit, meanderSeed);
        }
        SourceChannel channel = new SourceChannel(cell, exit.get(), meanderSeed);
        RiverSpline main = channel.toExit(channel.origin(-0.6, 0));
        return List.of(channel.rill(main, -0.125, 0.5), channel.rill(main, 0.125, 0.5),
            t -> main.pointAt(0.5 + t * 0.5));
    }
}
