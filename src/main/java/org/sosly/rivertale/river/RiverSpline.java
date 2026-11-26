package org.sosly.rivertale.river;

import net.minecraft.world.phys.Vec3;

/** One continuous path in downstream order, in world block coordinates. */
@FunctionalInterface
public interface RiverSpline {
    /** Samples the path from its start (0) to its end (1). */
    Vec3 pointAt(double t);
}
