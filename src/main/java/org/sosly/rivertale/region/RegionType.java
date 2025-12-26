package org.sosly.rivertale.region;

import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public enum RegionType {
    OCEANIC(new float[]{0.0f, 0.3f, 1.0f, 0.8f}),
    COASTAL(new float[]{0.0f, 1.0f, 1.0f, 0.8f}),
    FLUVIAL(new float[]{0.0f, 1.0f, 0.3f, 0.8f}),
    INLAND(new float[]{0.5f, 0.5f, 0.5f, 0.5f});

    public final float[] color;

    RegionType(float[] color) {
        this.color = color;
    }

    public static RegionType classify(RegionPos pos, CellCache cellCache) {
        Timer.Record record = Store.getTimer(RegionType.class, "classify").start();

        boolean allOcean = true;
        boolean anyOcean = false;

        CellPos min = pos.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();
        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                Cell cell = cellCache.getOrCompute(new CellPos(min.x() + x, min.z() + z));
                boolean ocean = cell.sample().isOcean();
                allOcean &= ocean;
                anyOcean |= ocean;
            }
        }

        if (allOcean) {
            record.stop();
            return OCEANIC;
        }
        if (anyOcean) {
            record.stop();
            return COASTAL;
        }

        for (Direction dir : Direction.D4) {
            if (isCoastal(pos.relative(dir), cellCache)) {
                record.stop();
                return FLUVIAL;
            }
        }

        record.stop();
        return INLAND;
    }

    private static boolean isCoastal(RegionPos pos, CellCache cellCache) {
        Timer.Record record = Store.getTimer(RegionType.class, "isCoastal").start();

        boolean hasOcean = false;
        boolean hasLand = false;

        CellPos min = pos.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();
        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                Cell cell = cellCache.getOrCompute(new CellPos(min.x() + x, min.z() + z));
                if (cell.sample().isOcean()) {
                    hasOcean = true;
                } else {
                    hasLand = true;
                }
                if (hasOcean && hasLand) {
                    record.stop();
                    return true;
                }
            }
        }

        record.stop();
        return false;
    }
}
