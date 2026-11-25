package org.sosly.rivertale.cell.feature;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.river.Watershed;

/**
 * A bifurcation occurs when ground directly ahead rises above the approaching river's water level
 * and downhill routes on either side of the obstacle reach different outlets. Each branch belongs
 * to its outlet's watershed. The watersheds share the upstream reach, but the incoming flow is
 * divided at the fork rather than counted in full for both branches.
 *
 * <p>Revisit this after the rest of the river pipeline is working. Tracing must verify both routes,
 * and the current watershed model supports only one downstream link per cell.
 */
public class Bifurcation implements FeatureHandler {
    @Override
    public boolean classify(Cell cell, Watershed watershed) {
        return false;
    }
}
