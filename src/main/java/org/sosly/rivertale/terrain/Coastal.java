package org.sosly.rivertale.terrain;

import java.util.ArrayList;
import java.util.List;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public final class Coastal {
    private Coastal() {
    }

    public static List<Boundary> boundaries(RegionPos region, CellCache cellCache) {
        Timer.Record timer = Store.getTimer(Coastal.class, "boundaries").start();
        List<Boundary> boundaries = new ArrayList<>();

        CellPos minCell = region.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();

        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                CellPos cellPos = new CellPos(minCell.x() + x, minCell.z() + z);
                Cell cell = cellCache.getOrCompute(cellPos);

                if (cell.isOcean() || cell.isBasin()) {
                    continue;
                }

                for (FlowDirection dir : FlowDirection.D4) {
                    CellPos neighborPos = cellPos.relative(dir);
                    Cell neighbor = cellCache.getOrCompute(neighborPos);

                    if (neighbor.isOcean()) {
                        boundaries.add(new Boundary(cellPos, neighborPos, BoundaryType.OCEAN));
                    }
                    if (neighbor.isBasin()) {
                        boundaries.add(new Boundary(cellPos, neighborPos, BoundaryType.BASIN));
                    }
                }
            }
        }

        timer.stop();
        return boundaries;
    }
}
