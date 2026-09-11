package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionExplorer;
import org.sosly.rivertale.world.RiverShaping;

public final class FlowlinePreview {
    private FlowlinePreview() {
    }

    public static List<Flowline> trace(Region region) {
        return trace(region, () -> RegionExplorer.discover(region.pos().getWorldPosition(0)));
    }

    static List<Flowline> trace(Region region, Set<Region> workingSet) {
        return trace(region, () -> workingSet);
    }

    private static List<Flowline> trace(Region region, Supplier<Set<Region>> regions) {
        Timer.Record timer = Store.getTimer(FlowlinePreview.class, "trace").start();
        try {
            Set<Region> workingSet = regions.get();
            if (workingSet.stream().allMatch(candidate -> candidate.boundaries().isEmpty())) {
                return List.of();
            }

            FlowlineTracer tracer = new FlowlineTracer(pos -> RegionExplorer.discover(pos.getWorldPosition(0)),
                RiverShaping::classify, 5, 0);
            if (!tracer.hasDestinations(region.pos())) {
                return List.of();
            }
            List<Flowline> paths = new ArrayList<>();
            for (CellPos pos : region.cells()) {
                Cell source = RiverShaping.classify(pos);
                if (source.feature().type != CellType.SOURCE) {
                    continue;
                }
                Flowline path = tracer.trace(pos);
                if (path.isValid()) {
                    paths.add(path);
                }
            }
            return List.copyOf(paths);
        } finally {
            timer.stop();
        }
    }

}
