package org.sosly.rivertale.river;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;

public class WatershedCache {
    private static volatile WatershedCache instance;

    private final Map<CellPos, Watershed> finalized;
    private final Map<CellPos, CellPos> cellToTerminus;
    private final Map<RegionPos, Set<CellPos>> regionToTermini;

    private WatershedCache() {
        this.finalized = Collections.synchronizedMap(new HashMap<>());
        this.cellToTerminus = Collections.synchronizedMap(new HashMap<>());
        this.regionToTermini = Collections.synchronizedMap(new HashMap<>());
    }

    public static synchronized void init() {
        if (instance != null) {
            return;
        }
        instance = new WatershedCache();
    }

    public static WatershedCache get() {
        return instance;
    }

    public static void shutdown() {
        if (instance != null) {
            instance.clear();
            instance = null;
        }
    }

    public synchronized void replace(Collection<Watershed> watersheds) {
        Set<CellPos> replaced = new HashSet<>();
        for (Watershed watershed : watersheds) {
            for (CellPos cell : watershed.allCells()) {
                CellPos previous = cellToTerminus.get(cell);
                if (previous != null) {
                    replaced.add(previous);
                }
            }
        }
        replaced.forEach(this::remove);
        for (Watershed watershed : watersheds) {
            CellPos terminus = watershed.outletToSources().get(0);
            finalized.put(terminus, watershed);
            regionToTermini.computeIfAbsent(terminus.getRegion(), key -> new HashSet<>()).add(terminus);
            for (CellPos cell : watershed.allCells()) {
                cellToTerminus.put(cell, terminus);
            }
        }
    }

    private void remove(CellPos terminus) {
        Watershed watershed = finalized.remove(terminus);
        if (watershed != null) {
            for (CellPos cell : watershed.allCells()) {
                cellToTerminus.remove(cell, terminus);
            }
        }
        Set<CellPos> termini = regionToTermini.get(terminus.getRegion());
        if (termini != null) {
            termini.remove(terminus);
            if (termini.isEmpty()) {
                regionToTermini.remove(terminus.getRegion());
            }
        }
    }

    public synchronized Watershed getWatershed(CellPos cell) {
        CellPos terminus = cellToTerminus.get(cell);
        if (terminus == null) {
            return null;
        }
        return finalized.get(terminus);
    }

    public synchronized void evictForRegion(RegionPos region) {
        Set<CellPos> termini = regionToTermini.remove(region);
        if (termini == null) {
            return;
        }

        for (CellPos terminus : termini) {
            Watershed watershed = finalized.remove(terminus);
            if (watershed != null) {
                for (CellPos cell : watershed.allCells()) {
                    cellToTerminus.remove(cell);
                }
            }
        }
    }

    private void clear() {
        finalized.clear();
        cellToTerminus.clear();
        regionToTermini.clear();
    }
}
