package org.sosly.rivertale.data;

import net.minecraft.core.BlockPos;

import java.util.Set;

/**
 * Result of continent detection including all visited regions.
 */
public class ContinentDetectionResult {
    private final ContinentalData continentalData;
    private final Set<RegionKey> visitedRegions;
    private final ContinentId continentId;

    public ContinentDetectionResult(ContinentalData continentalData,
                                     Set<RegionKey> visitedRegions,
                                     ContinentId continentId) {
        this.continentalData = continentalData;
        this.visitedRegions = visitedRegions;
        this.continentId = continentId;
    }

    public ContinentalData getContinentalData() {
        return continentalData;
    }

    public Set<RegionKey> getVisitedRegions() {
        return visitedRegions;
    }

    public ContinentId getContinentId() {
        return continentId;
    }

    public BlockPos getCenter() {
        return continentalData.getCenter();
    }
}

