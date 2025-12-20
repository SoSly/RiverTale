package org.sosly.rivertale.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.sosly.rivertale.network.VisualizeD8Packet.D8RegionData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClientD8Cache {

    private static boolean enabled = false;
    private static final Map<RegionKey, D8RegionData> REGIONS = new HashMap<>();

    public static void setEnabled(boolean enabled) {
        ClientD8Cache.enabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void updateRegions(List<D8RegionData> newRegions) {
        REGIONS.clear();
        for (D8RegionData region : newRegions) {
            REGIONS.put(new RegionKey(region.regionX, region.regionZ), region);
        }
    }

    public static List<D8RegionData> getRegions() {
        return new ArrayList<>(REGIONS.values());
    }

    public static D8RegionData getRegion(int regionX, int regionZ) {
        return REGIONS.get(new RegionKey(regionX, regionZ));
    }

    public static void clear() {
        REGIONS.clear();
    }

    private record RegionKey(int x, int z) {}
}
