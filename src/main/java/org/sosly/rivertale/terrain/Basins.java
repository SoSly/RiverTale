package org.sosly.rivertale.terrain;

import java.util.ArrayList;
import java.util.List;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public final class Basins {
    private Basins() {
    }

    public static Boundary boundaries(RegionPos region, CellCache cellCache) {
        Timer.Record timer = Store.getTimer(Basins.class, "boundaries").start();
        List<CellPos> basinCells = new ArrayList<>();

        CellPos minCell = region.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();

        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                CellPos cellPos = new CellPos(minCell.x() + x, minCell.z() + z);
                Cell cell = cellCache.getOrCompute(cellPos);

                if (cell.isBasin()) {
                    basinCells.add(cellPos);
                }
            }
        }

        timer.stop();

        if (basinCells.isEmpty()) {
            return null;
        }

        return new Boundary(basinCells, BoundaryType.BASIN);
    }
}
