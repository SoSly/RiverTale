package org.sosly.rivertale.cell.feature;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.world.RiverShaping;

public class Crater implements FeatureHandler {
    private static final int HEIGHT_THRESHOLD = 100;
    private static final double WARM_THRESHOLD = 0.2;
    private static final double CRATER_CHANCE = 0.05;
    private static final ResourceLocation CRATER_RANDOM = new ResourceLocation("rivertale", "crater");

    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        if (cell.averageEstimatedTerrainHeight() < HEIGHT_THRESHOLD) {
            return false;
        }
        if (cell.hasInflowingNeighbor()) {
            return false;
        }
        if (cell.averageTemperature() < WARM_THRESHOLD) {
            return false;
        }
        RandomSource random = RiverShaping.getRandomState()
            .getOrCreateRandomFactory(CRATER_RANDOM)
            .at(cell.pos().getMiddleBlockX(), 0, cell.pos().getMiddleBlockZ());
        return random.nextFloat() < CRATER_CHANCE;
    }
}
