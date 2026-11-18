package org.sosly.rivertale.river;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

final class RiverNetwork {
    private RiverNetwork() {
    }

    static Built build(RegionPos center, Set<RegionPos> roots) {
        Timer.Record timer = Store.getTimer(RiverNetwork.class, "build").start();
        try {
            PreparedRiverData data = RiverPreparation.prepare(center, roots, false);
            FlowlineTracer tracer = new FlowlineTracer(data);
            FlowlinePreparation.Result prepared = FlowlinePreparation.prepare(tracer.region(center), tracer);
            List<Watershed> watersheds = WatershedBuilder.build(WatershedPreparation.gather(prepared));
            return new Built(data, tracer, prepared.traced(), watersheds);
        } finally {
            timer.stop();
        }
    }

    record Built(PreparedRiverData data, FlowlineTracer tracer, Map<RegionPos, List<Flowline>> traced,
                 List<Watershed> watersheds) {
        Built {
            traced = Map.copyOf(traced);
            watersheds = List.copyOf(watersheds);
        }
    }
}
