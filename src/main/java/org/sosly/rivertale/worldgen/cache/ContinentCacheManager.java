package org.sosly.rivertale.worldgen.cache;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.DensityFunction;
import org.sosly.rivertale.data.ContinentDetectionResult;
import org.sosly.rivertale.data.ContinentId;
import org.sosly.rivertale.data.ContinentalData;
import org.sosly.rivertale.data.RegionKey;
import org.sosly.rivertale.worldgen.analysis.ContinentDetector;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Two-level cache manager for continent detection integrated with SavedData persistence.
 */
public class ContinentCacheManager {
    private final ConcurrentHashMap<RegionKey, ContinentId> regionCache;
    private final ConcurrentHashMap<ContinentId, ContinentalData> continentCache;
    private final ContinentDetector detector;
    private final ContinentCacheSavedData savedData;
    private final ServerLevel level;

    private ContinentCacheManager(ContinentCacheSavedData savedData, ServerLevel level) {
        this.savedData = savedData;
        this.level = level;
        this.regionCache = savedData.getRegionCache();
        this.continentCache = savedData.getContinentCache();
        this.detector = new ContinentDetector();
    }

    /**
     * Get or create cache manager for a ServerLevel.
     * Only works for Overworld dimension.
     *
     * @param level the server level
     * @return cache manager, or null if not Overworld
     */
    public static ContinentCacheManager forLevel(ServerLevel level) {
        if (!level.dimension().equals(Level.OVERWORLD)) {
            return null;
        }

        ContinentCacheSavedData savedData = level.getDataStorage().computeIfAbsent(
            ContinentCacheSavedData::load,
            ContinentCacheSavedData::new,
            ContinentCacheSavedData.FILE_ID
        );

        return new ContinentCacheManager(savedData, level);
    }

    /**
     * Get continental data for a position, using cache when possible.
     *
     * @param pos position to query
     * @param depthFunction Lithosphere's depth density function
     * @param erosionFunction Lithosphere's erosion density function
     * @return continental data with center and statistics
     */
    public ContinentalData getContinentalData(BlockPos pos,
                                               DensityFunction depthFunction,
                                               DensityFunction erosionFunction) {
        RegionKey region = RegionKey.fromBlockPos(pos);
        ContinentId continentId = regionCache.get(region);

        if (continentId != null) {
            ContinentalData cached = continentCache.get(continentId);
            if (cached != null) {
                return cached;
            }
        }

        return computeAndCache(pos, depthFunction, erosionFunction);
    }

    private synchronized ContinentalData computeAndCache(BlockPos pos,
                                                          DensityFunction depthFunction,
                                                          DensityFunction erosionFunction) {
        RegionKey region = RegionKey.fromBlockPos(pos);
        ContinentId continentId = regionCache.get(region);

        if (continentId != null) {
            ContinentalData cached = continentCache.get(continentId);
            if (cached != null) {
                return cached;
            }
        }

        ContinentDetectionResult result = detector.detectContinent(pos, depthFunction, erosionFunction, level);
        continentId = result.getContinentId();
        ContinentalData data = result.getContinentalData();

        continentCache.put(continentId, data);
        for (RegionKey visitedRegion : result.getVisitedRegions()) {
            regionCache.put(visitedRegion, continentId);
        }

        savedData.setDirty();

        return data;
    }

    /**
     * Get cache statistics for debugging.
     *
     * @return string describing cache state
     */
    public String getCacheStats() {
        return String.format("Regions cached: %d, Continents cached: %d",
            regionCache.size(), continentCache.size());
    }
}
