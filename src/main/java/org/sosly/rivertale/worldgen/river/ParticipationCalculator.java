package org.sosly.rivertale.worldgen.river;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.config.RiverConfig;

public class ParticipationCalculator {

    public static boolean isParticipating(RiverCellKey key, RandomState randomState) {
        PositionalRandomFactory factory = randomState.getOrCreateRandomFactory(
            new ResourceLocation("rivertale", "participation"));
        RandomSource rng = factory.at(key.cellX(), 0, key.cellZ());
        return rng.nextDouble() < RiverConfig.PARTICIPATION_RATE.get();
    }
}
