package org.sosly.rivertale.worldgen.river;

import java.util.Random;

public class ParticipationCalculator {

    private static final double PARTICIPATION_RATE = 0.7;

    public static boolean isParticipating(RiverCellKey key, long worldSeed) {
        long seed = key.cellX() * 31L + key.cellZ() * 17L;
        Random rng = new Random(seed ^ worldSeed);
        return rng.nextDouble() < PARTICIPATION_RATE;
    }
}
