package org.sosly.rivertale.region;

import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
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

    public static RegionType classify(RegionPos pos, SampleCache sampleCache) {
        Timer.Record record = Store.getTimer(RegionType.class, "classify").start();

        boolean allOcean = true;
        boolean anyOcean = false;

        CellPos min = pos.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();
        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                CellPos cellPos = new CellPos(min.x() + x, min.z() + z);
                Sample sample = sampleCache.getOrCompute(cellPos.getMiddleBlockX(), cellPos.getMiddleBlockZ());
                boolean ocean = sample.isOcean();
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

        for (FlowDirection dir : FlowDirection.D4) {
            if (isCoastal(pos.relative(dir), sampleCache)) {
                record.stop();
                return FLUVIAL;
            }
        }

        record.stop();
        return INLAND;
    }

    private static boolean isCoastal(RegionPos pos, SampleCache sampleCache) {
        Timer.Record record = Store.getTimer(RegionType.class, "isCoastal").start();

        boolean hasOcean = false;
        boolean hasLand = false;

        CellPos min = pos.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();
        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                CellPos cellPos = new CellPos(min.x() + x, min.z() + z);
                Sample sample = sampleCache.getOrCompute(cellPos.getMiddleBlockX(), cellPos.getMiddleBlockZ());
                if (sample.isOcean()) {
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
