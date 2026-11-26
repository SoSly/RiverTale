package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.river.RiverSpline;
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

    @Override
    public List<RiverSpline> buildSplines(Cell cell, List<Cell> entries, Optional<Cell> exit, long meanderSeed) {
        if (!entries.isEmpty() || exit.isEmpty()) {
            return FeatureHandler.super.buildSplines(cell, entries, exit, meanderSeed);
        }
        SourceChannel channel = new SourceChannel(cell, exit.get(), meanderSeed);
        Vec3 rim = channel.origin(0.25, 0);
        Vec3 lip = channel.origin(0.375, 0);
        return List.of(t -> rim.lerp(lip, t), channel.toExit(lip));
    }
}
