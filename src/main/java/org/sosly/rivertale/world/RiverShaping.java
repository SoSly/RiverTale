package org.sosly.rivertale.world;

import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.RandomState;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public class RiverShaping {
    private static int seaLevel = 63;
    private static volatile RandomState randomState;

    private RiverShaping() {}

    public static void init(int waterLevel, RandomState state) {
        seaLevel = waterLevel;
        randomState = state;
    }

    public static void shutdown() {
        randomState = null;
        seaLevel = 63;
    }

    public static boolean isActive(RandomState state) {
        return state != null && state == randomState;
    }

    public static int getSeaLevel() {
        return seaLevel;
    }

    public static RandomState getRandomState() {
        return randomState;
    }

    public static void shape(ChunkAccess chunk, RandomState state) {
        if (!isActive(state)) {
            return;
        }

        CellPos pos = new CellPos(chunk.getPos().getMiddleBlockPosition(0));
        if (pos.getRegion().containsOutOfBoundsCells()) {
            return;
        }

        Timer.Record timer = Store.getTimer(RiverShaping.class, "shape").start();
        try {
            preclassify(pos);
        } finally {
            timer.stop();
        }
    }

    public static Cell preclassify(CellPos pos) {
        CellCache cache = CellCache.get();
        Cell cell = cache.getOrComputeWithFlow(pos);
        if (cell.isOcean()) {
            return cell;
        }

        Cell classified = Feature.classify(cell, null);
        cache.put(classified);
        return classified;
    }
}
