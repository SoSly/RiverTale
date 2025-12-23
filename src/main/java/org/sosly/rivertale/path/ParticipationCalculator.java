package org.sosly.rivertale.path;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.RegionPos;

public class ParticipationCalculator {

    public static boolean isParticipating(RegionPos regionPos, RandomState randomState) {
        PositionalRandomFactory factory = randomState.getOrCreateRandomFactory(
            new ResourceLocation("rivertale", "participation"));
        RandomSource rng = factory.at(regionPos.x(), 0, regionPos.z());
        return rng.nextDouble() < RiverConfig.PARTICIPATION_RATE.get();
    }
}
