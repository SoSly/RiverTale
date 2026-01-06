package org.sosly.rivertale.river;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;

public class WatershedCache {
    private static volatile WatershedCache instance;

    private final Map<CellPos, PendingWatershed> pending;
    private final Map<CellPos, Watershed> finalized;
    private final Map<CellPos, CellPos> cellToTerminus;
    private final Map<RegionPos, Set<CellPos>> regionToTermini;

    private WatershedCache() {
        this.pending = Collections.synchronizedMap(new HashMap<>());
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

    public void addPath(Path path) {
        if (!path.isValid()) {
            return;
        }

        CellPos terminus = path.terminus();
        if (terminus == null) {
            return;
        }

        synchronized (pending) {
            PendingWatershed pw = pending.computeIfAbsent(terminus, PendingWatershed::new);
            pw.addPath(path);
        }
    }

    public void finalizeReady(Set<RegionPos> loadedRegions) {
        synchronized (pending) {
            Iterator<Map.Entry<CellPos, PendingWatershed>> it = pending.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<CellPos, PendingWatershed> entry = it.next();
                CellPos terminus = entry.getKey();
                PendingWatershed pw = entry.getValue();
                it.remove();
                finalizeWatershed(terminus, pw);
            }
        }
    }

    private void finalizeWatershed(CellPos terminus, PendingWatershed pw) {
        RegionPos regionPos = terminus.getRegion();
        Watershed watershed = new Watershed(regionPos, pw.paths());

        finalized.put(terminus, watershed);
        regionToTermini.computeIfAbsent(regionPos, k -> new HashSet<>()).add(terminus);

        for (CellPos cell : watershed.allCells()) {
            cellToTerminus.put(cell, terminus);
        }
    }

    public Watershed getWatershed(CellPos cell) {
        CellPos terminus = cellToTerminus.get(cell);
        if (terminus == null) {
            return null;
        }
        return finalized.get(terminus);
    }

    public void evictForRegion(RegionPos region) {
        Set<CellPos> termini = regionToTermini.remove(region);
        if (termini == null) {
            return;
        }

        for (CellPos terminus : termini) {
            pending.remove(terminus);
            Watershed watershed = finalized.remove(terminus);
            if (watershed != null) {
                for (CellPos cell : watershed.allCells()) {
                    cellToTerminus.remove(cell);
                }
            }
        }
    }

    private void clear() {
        pending.clear();
        finalized.clear();
        cellToTerminus.clear();
        regionToTermini.clear();
    }
}
