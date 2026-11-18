package org.sosly.rivertale.cell.feature;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.world.RiverShaping;

public class Crater implements FeatureHandler {
    private static final ResourceLocation CRATER_RANDOM = new ResourceLocation("rivertale", "crater");

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        SourceConfig config = CommonConfig.get().sources();
        if (cell.averageEstimatedTerrainHeight() < config.craterMinHeight()) {
            return false;
        }
        if (cell.hasInflowingNeighbor()) {
            return false;
        }
        if (cell.averageTemperature() < config.craterMinTemperature()) {
            return false;
        }
        RandomSource random = RiverShaping.getRandomState()
            .getOrCreateRandomFactory(CRATER_RANDOM)
            .at(cell.pos().getMiddleBlockX(), 0, cell.pos().getMiddleBlockZ());
        return random.nextFloat() < config.craterChance();
    }
}
