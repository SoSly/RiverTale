package org.sosly.rivertale.cell.feature;

import javax.annotation.Nullable;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.river.Watershed;

public enum Feature {
    // Sources (ordered by specificity: most specific first)
    SNOWMELT(CellType.SOURCE, new Snowmelt(), new float[]{1.0f, 0.0f, 1.0f, 0.9f}),
    RESURGENCE(CellType.SOURCE, new Resurgence(), new float[]{0.0f, 1.0f, 0.0f, 0.9f}),
    SEEP(CellType.SOURCE, new Seep(), new float[]{1.0f, 1.0f, 0.0f, 0.9f}),
    CRATER(CellType.SOURCE, new Crater(), new float[]{1.0f, 0.5f, 0.0f, 0.9f}),
    SPRING(CellType.SOURCE, new Spring(), new float[]{0.0f, 1.0f, 1.0f, 0.9f}),

    // Termini
    DELTA(CellType.TERMINUS, new Delta(), new float[]{0.5f, 0.0f, 0.7f, 0.9f}),
    ESTUARY(CellType.TERMINUS, new Estuary(), new float[]{0.6f, 0.1f, 0.8f, 0.9f}),
    WETLAND(CellType.TERMINUS, new Wetland(), new float[]{0.3f, 0.5f, 0.4f, 0.9f}),
    MOUTH(CellType.TERMINUS, new Mouth(), new float[]{0.7f, 0.2f, 0.9f, 0.9f}),

    // Lakes
    ENDORHEIC(CellType.LAKE, new Endorheic(), new float[]{0.2f, 0.6f, 0.9f, 0.9f}),
    KETTLE(CellType.LAKE, new Kettle(), new float[]{0.0f, 0.7f, 0.7f, 0.9f}),
    SINKHOLE(CellType.LAKE, new Sinkhole(), new float[]{0.1f, 0.3f, 0.6f, 0.9f}),
    LAGOON(CellType.LAKE, new Lagoon(), new float[]{0.3f, 0.8f, 0.9f, 0.9f}),
    TECTONIC(CellType.LAKE, new Tectonic(), new float[]{0.4f, 0.5f, 0.8f, 0.9f}),


    // Junctions
    CONFLUENCE(CellType.JUNCTION, new Confluence(), new float[]{0.0f, 0.8f, 0.2f, 0.9f}),
    BIFURCATION(CellType.JUNCTION, new Bifurcation(), new float[]{0.2f, 1.0f, 0.0f, 0.9f}),
    BRAID(CellType.JUNCTION, new Braid(), new float[]{0.4f, 0.9f, 0.1f, 0.9f}),

    // Courses
    PLUNGE_POOL(CellType.COURSE, new PlungePool(), new float[]{0.2f, 0.4f, 0.9f, 0.9f}),
    RAPIDS(CellType.COURSE, new Rapids(), new float[]{0.3f, 0.5f, 1.0f, 0.9f}),
    CASCADE(CellType.COURSE, new Cascade(), new float[]{0.0f, 0.5f, 0.7f, 0.9f}),
    WATERFALL(CellType.COURSE, new Waterfall(), new float[]{0.1f, 0.3f, 0.8f, 0.9f}),
    RUN(CellType.COURSE, new Run(), new float[]{0.0f, 0.3f, 1.0f, 0.8f}),

    // Fallback
    DIVIDE(CellType.NONE, new Divide(), new float[]{1.0f, 0.5f, 0.5f, 0.9f}),
    DEFAULT(CellType.NONE, new Default(), new float[]{0.8f, 0.8f, 0.8f, 0.9f});

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
                return new Cell(cell.pos(), cell.sample(), feature, cell.flowDirections());
            }
        }

        record.stop();
        return new Cell(cell.pos(), cell.sample(), DEFAULT, cell.flowDirections());
    }
}
