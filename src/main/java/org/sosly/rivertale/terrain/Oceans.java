package org.sosly.rivertale.terrain;

import java.util.HashSet;
import java.util.Set;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public final class Oceans {
    private Oceans() {
    }

    public static Set<OceanBoundary> boundaries(RegionPos region, SampleCache sampleCache) {
        Timer.Record timer = Store.getTimer(Oceans.class, "boundaries").start();
        Set<OceanBoundary> result = new HashSet<>();

        CellPos minCell = region.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();

        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                CellPos cellPos = new CellPos(minCell.x() + x, minCell.z() + z);
                Sample sample = sampleCache.getOrCompute(cellPos.getMiddleBlockX(), cellPos.getMiddleBlockZ());

                if (sample.isOcean()) {
                    continue;
                }

                for (Direction dir : Direction.D4) {
                    CellPos neighborPos = cellPos.relative(dir);
                    Sample neighborSample = sampleCache.getOrCompute(neighborPos.getMiddleBlockX(), neighborPos.getMiddleBlockZ());

                    if (neighborSample.isOcean()) {
                        result.add(new OceanBoundary(cellPos, neighborPos));
                    }
                }
            }
        }

        timer.stop();
        return result;
    }
}
