package org.sosly.rivertale.cell.feature;

import javax.annotation.Nullable;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.river.Watershed;

public enum Feature {
    // Sources (ordered by specificity: most specific first)
    SNOWMELT(CellType.SOURCE, new Snowmelt()),
    RESURGENCE(CellType.SOURCE, new Resurgence()),
    SEEP(CellType.SOURCE, new Seep()),
    CRATER(CellType.SOURCE, new Crater()),
    SPRING(CellType.SOURCE, new Spring()),

    // Termini
    DELTA(CellType.TERMINUS, new Delta()),
    ESTUARY(CellType.TERMINUS, new Estuary()),
    WETLAND(CellType.TERMINUS, new Wetland()),
    MOUTH(CellType.TERMINUS, new Mouth()),

    // Junctions
    CONFLUENCE(CellType.JUNCTION, new Confluence()),
    BIFURCATION(CellType.JUNCTION, new Bifurcation()),
    BRAID(CellType.JUNCTION, new Braid()),

    // Lakes
    ENDORHEIC(CellType.LAKE, new Endorheic()),
    KETTLE(CellType.LAKE, new Kettle()),
    SINKHOLE(CellType.LAKE, new Sinkhole()),
    LAGOON(CellType.LAKE, new Lagoon()),
    TECTONIC(CellType.LAKE, new Tectonic()),

    // Courses (ordered by drop size: largest first, then fallback)
    PLUNGE_POOL(CellType.COURSE, new PlungePool()),
    WATERFALL(CellType.COURSE, new Waterfall()),
    CASCADE(CellType.COURSE, new Cascade()),
    RAPIDS(CellType.COURSE, new Rapids()),
    RUN(CellType.COURSE, new Run()),

    // Fallback
    DIVIDE(CellType.NONE, new Divide()),
    NONE(CellType.NONE, new None());

    public final CellType type;
    public final FeatureHandler handler;
    private static final Feature[] VALUES = values();

    Feature(CellType type, FeatureHandler handler) {
        this.handler = handler;
        this.type = type;
    }

    public static Cell classify(Cell cell, @Nullable Watershed watershed) {
        if (cell.isOcean()) {
            return cell.withFeature(NONE);
        }

        Timer.Record record = Store.getTimer(Feature.class, "classify").start();

        for (Feature feature : VALUES) {
            if (feature.handler.classify(cell, watershed)) {
                record.stop();
                return cell.withFeature(feature);
            }
        }

        record.stop();
        return cell.withFeature(NONE);
    }
}
