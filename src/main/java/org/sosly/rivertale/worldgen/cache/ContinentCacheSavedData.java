package org.sosly.rivertale.worldgen.cache;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.saveddata.SavedData;
import org.sosly.rivertale.data.ContinentId;
import org.sosly.rivertale.data.ContinentalData;
import org.sosly.rivertale.data.RegionKey;
import net.minecraft.core.BlockPos;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists continent cache data to disk using Minecraft's SavedData system.
 */
public class ContinentCacheSavedData extends SavedData {
    public static final String FILE_ID = "rivertale_continents";
    private static final String TAG_REGION_CACHE = "RegionCache";
    private static final String TAG_CONTINENT_CACHE = "ContinentCache";

    private final ConcurrentHashMap<RegionKey, ContinentId> regionCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<ContinentId, ContinentalData> continentCache = new ConcurrentHashMap<>();

    public ContinentCacheSavedData() {
    }

    public static ContinentCacheSavedData load(CompoundTag tag) {
        ContinentCacheSavedData data = new ContinentCacheSavedData();

        ListTag regionList = tag.getList(TAG_REGION_CACHE, 10);
        for (int i = 0; i < regionList.size(); i++) {
            CompoundTag regionEntry = regionList.getCompound(i);
            int[] regionCoords = regionEntry.getIntArray("Region");
            if (regionCoords.length == 2) {
                RegionKey region = new RegionKey(regionCoords[0], regionCoords[1]);
                ContinentId continentId = new ContinentId(regionEntry.getLong("ContinentId"));
                data.regionCache.put(region, continentId);
            }
        }

        ListTag continentList = tag.getList(TAG_CONTINENT_CACHE, 10);
        for (int i = 0; i < continentList.size(); i++) {
            CompoundTag continentEntry = continentList.getCompound(i);
            ContinentId id = new ContinentId(continentEntry.getLong("Id"));
            int[] centerCoords = continentEntry.getIntArray("Center");
            if (centerCoords.length == 3) {
                BlockPos center = new BlockPos(centerCoords[0], centerCoords[1], centerCoords[2]);
                double maxContinentalness = continentEntry.getDouble("MaxContinentalness");
                int highPoints = continentEntry.getInt("HighPoints");
                int samples = continentEntry.getInt("Samples");
                ContinentalData continentalData = new ContinentalData(center, maxContinentalness, highPoints, samples);
                data.continentCache.put(id, continentalData);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag regionList = new ListTag();
        regionCache.forEach((region, continentId) -> {
            CompoundTag entry = new CompoundTag();
            entry.putIntArray("Region", new int[]{region.getRegionX(), region.getRegionZ()});
            entry.putLong("ContinentId", continentId.getId());
            regionList.add(entry);
        });
        tag.put(TAG_REGION_CACHE, regionList);

        ListTag continentList = new ListTag();
        continentCache.forEach((id, data) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Id", id.getId());
            BlockPos center = data.getCenter();
            entry.putIntArray("Center", new int[]{center.getX(), center.getY(), center.getZ()});
            entry.putDouble("MaxContinentalness", data.getMaxContinentalness());
            entry.putInt("HighPoints", data.getHighPointCount());
            entry.putInt("Samples", data.getTotalSamples());
            continentList.add(entry);
        });
        tag.put(TAG_CONTINENT_CACHE, continentList);

        return tag;
    }

    public ConcurrentHashMap<RegionKey, ContinentId> getRegionCache() {
        return regionCache;
    }

    public ConcurrentHashMap<ContinentId, ContinentalData> getContinentCache() {
        return continentCache;
    }
}

