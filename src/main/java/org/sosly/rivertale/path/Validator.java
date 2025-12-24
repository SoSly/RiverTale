package org.sosly.rivertale.path;

import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.path.validators.LengthValidator;
import org.sosly.rivertale.path.validators.TributaryValidator;
import org.sosly.rivertale.path.validators.ValidationContext;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.DensityProvider;

public class Validator {

    private Validator() {
    }

    public static Flow validate(
            Flow flow,
            RegionPos regionPos,
            RegionType featureRegionType,
            DensityProvider provider,
            RandomState randomState) {

        ValidationContext ctx = new ValidationContext(regionPos, featureRegionType, provider, randomState);
        MutableFlowState state = new MutableFlowState(flow);

        LengthValidator.validate(state, ctx);
        TributaryValidator.validate(state, ctx);

        return state.toFlow();
    }
}
