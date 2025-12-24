package org.sosly.rivertale.path.validators;

import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.DensityProvider;

public record ValidationContext(
    RegionPos regionPos,
    RegionType featureRegionType,
    DensityProvider provider,
    RandomState randomState
) {
    private static final int DEFAULT_MINIMUM_RIVER_LENGTH = 3;

    public int minimumRiverLength() {
        try {
            return RiverConfig.MINIMUM_RIVER_LENGTH.get();
        } catch (IllegalStateException e) {
            return DEFAULT_MINIMUM_RIVER_LENGTH;
        }
    }
}
