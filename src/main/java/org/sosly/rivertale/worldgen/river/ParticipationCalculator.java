package org.sosly.rivertale.worldgen.river;

import java.util.Random;

public class ParticipationCalculator {

    private static final double BASE_PARTICIPATION_RATE = 0.7;
    private static final double PARTICIPATION_DECAY = 0.25;

    public static boolean isParticipating(RiverCellKey key, long worldSeed) {
        double rate = BASE_PARTICIPATION_RATE * Math.pow(1.0 - PARTICIPATION_DECAY, key.pass() - 1);

        long seed = key.cellX() * 31L + key.cellZ() * 17L + key.pass() * 7L;
        Random rng = new Random(seed ^ worldSeed);
        return rng.nextDouble() < rate;
    }
}
