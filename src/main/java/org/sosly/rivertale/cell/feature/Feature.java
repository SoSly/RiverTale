package org.sosly.rivertale.cell.feature;

import javax.annotation.Nullable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.river.Watershed;

public enum Feature {
    // Sources (ordered by specificity: most specific first)
    SNOWMELT(CellType.SOURCE, new Snowmelt(), new float[]{1f, 0f, 0f, 1f}),
    RESURGENCE(CellType.SOURCE, new Resurgence(), new float[]{0f, 1f, 0f, 1f}),
    SEEP(CellType.SOURCE, new Seep(), new float[]{0f, 0f, 1f, 1f}),
    CRATER(CellType.SOURCE, new Crater(), new float[]{1f, 1f, 0f, 1f}),
    SPRING(CellType.SOURCE, new Spring(), new float[]{0f, 1f, 1f, 1f}),

    // Termini
    DELTA(CellType.TERMINUS, new Delta(), new float[]{1f, 0f, 0f, 1f}),
    ESTUARY(CellType.TERMINUS, new Estuary(), new float[]{0f, 1f, 0f, 1f}),
    WETLAND(CellType.TERMINUS, new Wetland(), new float[]{0f, 0f, 1f, 1f}),
    MOUTH(CellType.TERMINUS, new Mouth(), new float[]{1f, 1f, 0f, 1f}),

    // Lakes
    ENDORHEIC(CellType.LAKE, new Endorheic(), new float[]{1f, 0f, 0f, 1f}),
    KETTLE(CellType.LAKE, new Kettle(), new float[]{0f, 1f, 0f, 1f}),
    SINKHOLE(CellType.LAKE, new Sinkhole(), new float[]{0f, 0f, 1f, 1f}),
    LAGOON(CellType.LAKE, new Lagoon(), new float[]{1f, 1f, 0f, 1f}),
    TECTONIC(CellType.LAKE, new Tectonic(), new float[]{0f, 1f, 1f, 1f}),

    // Junctions
    CONFLUENCE(CellType.JUNCTION, new Confluence(), new float[]{1f, 0f, 0f, 1f}),
    BIFURCATION(CellType.JUNCTION, new Bifurcation(), new float[]{0f, 1f, 0f, 1f}),
    BRAID(CellType.JUNCTION, new Braid(), new float[]{0f, 0f, 1f, 1f}),

    // Courses (ordered by drop size: largest first, then fallback)
    PLUNGE_POOL(CellType.COURSE, new PlungePool(), new float[]{1f, 0f, 0f, 1f}),
    WATERFALL(CellType.COURSE, new Waterfall(), new float[]{0f, 1f, 0f, 1f}),
    CASCADE(CellType.COURSE, new Cascade(), new float[]{0f, 0f, 1f, 1f}),
    RAPIDS(CellType.COURSE, new Rapids(), new float[]{1f, 1f, 0f, 1f}),
    RUN(CellType.COURSE, new Run(), new float[]{0f, 1f, 1f, 1f}),

    // Fallback
    DIVIDE(CellType.NONE, new Divide(), new float[]{1f, 1f, 1f, 1f}),
    DEFAULT(CellType.NONE, new Default(), new float[]{0.5f, 0.5f, 0.5f, 1f});

    public final CellType type;
    public final float[] color;

    private final FeatureHandler handler;
    private static final Feature[] VALUES = values();

    Feature(CellType type, FeatureHandler handler, float[] color) {
        this.handler = handler;
        this.type = type;
        this.color = color;
    }

    public static Cell classify(Cell cell, @Nullable Watershed watershed) {
        Timer.Record record = Store.getTimer(Feature.class, "classify").start();

        for (Feature feature : VALUES) {
            if (feature.handler.classify(cell, watershed)) {
                record.stop();
                return cell.withFeature(feature);
            }
        }

        record.stop();
        return cell.withFeature(DEFAULT);
    }

    public static void carve(Cell cell, Watershed watershed, ChunkAccess chunk) {
        Timer.Record carveTimer = Store.getTimer(Feature.class, "carve").start();

        int[][] targetSurface = cell.feature().handler.carve(cell, watershed, chunk);

        if (targetSurface == null) {
            carveTimer.stop();
            return;
        }

        applyCarve(chunk, targetSurface);
        carveTimer.stop();
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    private static void applyCarve(ChunkAccess chunk, int[][] targetSurface) {
        int maxY = chunk.getMaxBuildHeight() - 1;
        int minY = chunk.getMinBuildHeight();

        for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
                int targetY = targetSurface[localX][localZ];

                if (targetY == Integer.MAX_VALUE) {
                    continue;
                }

                int existingSurface = findSurfaceHeight(chunk, localX, localZ, minY, maxY);

                if (existingSurface > targetY) {
                    for (int y = targetY + 1; y <= existingSurface; y++) {
                        int sectionIndex = chunk.getSectionIndex(y);
                        chunk.getSection(sectionIndex).setBlockState(localX, y & 15, localZ, AIR, false);
                    }
                } else if (existingSurface < targetY) {
                    for (int y = existingSurface + 1; y <= targetY; y++) {
                        int sectionIndex = chunk.getSectionIndex(y);
                        chunk.getSection(sectionIndex).setBlockState(localX, y & 15, localZ, STONE, false);
                    }
                }
            }
        }
    }

    private static int findSurfaceHeight(ChunkAccess chunk, int localX, int localZ, int minY, int maxY) {
        for (int y = maxY; y >= minY; y--) {
            int sectionIndex = chunk.getSectionIndex(y);
            BlockState state = chunk.getSection(sectionIndex).getBlockState(localX, y & 15, localZ);
            if (!state.isAir()) {
                return y;
            }
        }
        return minY;
    }
}
