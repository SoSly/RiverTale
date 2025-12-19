package org.sosly.rivertale.worldgen.river;

import org.sosly.rivertale.config.RiverConfig;

import java.util.Random;

public class ParticipationCalculator {

    public static boolean isParticipating(RiverCellKey key, long worldSeed) {
        long seed = key.cellX() * 31L + key.cellZ() * 17L;
        Random rng = new Random(seed ^ worldSeed);
        return rng.nextDouble() < RiverConfig.PARTICIPATION_RATE.get();
    }
}
