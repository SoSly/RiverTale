package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
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
}
