package org.sosly.rivertale.river;

import java.util.List;
import java.util.Map;
import org.sosly.rivertale.cell.feature.Feature;
import javax.annotation.Nullable;
import org.sosly.rivertale.core.CellPos;

public record Flowline(List<CellPos> cells, @Nullable CellPos terminus, boolean isValid, List<Lake> lakes) {
    public record Lake(Feature feature, Map<CellPos, Integer> depths) {
        public Lake {
            depths = Map.copyOf(depths);
        }
    }

    public Flowline(List<CellPos> cells, @Nullable CellPos terminus, boolean isValid) {
        this(cells, terminus, isValid, List.of());
    }

    public Flowline {
        lakes = List.copyOf(lakes);
        cells = List.copyOf(cells);
    }

    public double lengthBlocks(int cellBlocks) {
        double length = 0;
        for (int i = 1; i < cells.size(); i++) {
            CellPos previous = cells.get(i - 1);
            CellPos current = cells.get(i);
            long dx = (long) current.x() - previous.x();
            long dz = (long) current.z() - previous.z();
            length += Math.hypot(dx, dz);
        }
        return length * cellBlocks;
    }
}
