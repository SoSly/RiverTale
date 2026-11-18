package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.List;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;

public final class SourceTrace {
    private SourceTrace() {
    }

    static List<Flowline> trace(Region region, FlowlineTracer tracer) {
        Timer.Record timer = Store.getTimer(SourceTrace.class, "trace").start();
        try {
            if (!tracer.hasDestinations(region.pos())) {
                return List.of();
            }
            List<Flowline> paths = new ArrayList<>();
            for (CellPos pos : tracer.sources(region.pos())) {
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
