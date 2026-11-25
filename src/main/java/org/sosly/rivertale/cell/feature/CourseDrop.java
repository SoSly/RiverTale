package org.sosly.rivertale.cell.feature;

import java.util.OptionalInt;
import javax.annotation.Nullable;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.Watershed;

final class CourseDrop {
    private static final int WATERFALL_MIN = 12;
    private static final int CASCADE_MIN = 5;
    private static final int RAPIDS_MIN = 2;

    private CourseDrop() {}

    static Feature classify(CellPos pos, @Nullable Watershed watershed) {
        if (watershed == null) {
            return Feature.NONE;
        }
        CellPos downstream = watershed.downstream(pos);
        if (downstream == null) {
            return Feature.NONE;
        }
        OptionalInt from = watershed.cellY(pos);
        OptionalInt to = watershed.cellY(downstream);
        if (from.isEmpty() || to.isEmpty()) {
            return Feature.NONE;
        }
        long drop = (long) from.getAsInt() - to.getAsInt();
        if (drop >= WATERFALL_MIN) {
            return Feature.WATERFALL;
        }
        if (drop >= CASCADE_MIN) {
            return Feature.CASCADE;
        }
        if (drop >= RAPIDS_MIN) {
            return Feature.RAPIDS;
        }
        return Feature.NONE;
    }
}
