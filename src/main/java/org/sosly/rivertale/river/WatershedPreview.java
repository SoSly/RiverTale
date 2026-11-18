package org.sosly.rivertale.river;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public final class WatershedPreview {
    private WatershedPreview() {}

    public static Result prepare(RegionPos center, Set<RegionPos> visible) {
        if (CommonConfig.get().localRiverPreview()) {
            PreparedRiverData data = RiverPreparation.prepare(center, visible, true);
            return tracePrepared(center, visible, new FlowlineTracer(data));
        }
        Store.getDistribution(WatershedPreview.class, "localOnly").record(0);
        RiverNetwork.Built network = RiverNetwork.build(center, visible);
        return new Result(visiblePaths(visible, network.traced(), network.tracer()), network.watersheds(),
            network.data(), center);
    }

    static Result tracePrepared(RegionPos center, Set<RegionPos> visible, FlowlineTracer tracer) {
        Timer.Record timer = Store.getTimer(WatershedPreview.class, "request").start();
        try {
            boolean local = CommonConfig.get().localRiverPreview();
            Store.getDistribution(WatershedPreview.class, "localOnly").record(local ? 1 : 0);
            if (local) {
                Map<RegionPos, List<Flowline>> paths = visiblePaths(visible, Map.of(), tracer);
                Store.getDistribution(WatershedPreview.class, "localSourceRegions").record(paths.size());
                List<Flowline> gathered = paths.values().stream().flatMap(List::stream).toList();
                return new Result(paths, WatershedBuilder.build(gathered), tracer.data(), center);
            }
            FlowlinePreparation.Result prepared = FlowlinePreparation.prepare(tracer.region(center), tracer);
            List<Watershed> watersheds = WatershedBuilder.build(WatershedPreparation.gather(prepared));
            return new Result(visiblePaths(visible, prepared.traced(), tracer), watersheds, tracer.data(), center);
        } finally {
            timer.stop();
        }
    }

    private static Map<RegionPos, List<Flowline>> visiblePaths(Set<RegionPos> visible,
                                                            Map<RegionPos, List<Flowline>> traced,
                                                            FlowlineTracer tracer) {
        Map<RegionPos, List<Flowline>> result = new LinkedHashMap<>();
        for (RegionPos pos : visible) {
            List<Flowline> paths = traced.get(pos);
            if (paths == null) {
                paths = SourceTrace.trace(tracer.region(pos), tracer);
            }
            result.put(pos, paths);
        }
        return result;
    }

    public record Result(Map<RegionPos, List<Flowline>> paths, List<Watershed> watersheds,
                         PreparedRiverData data, RegionPos center) {
        public CompoundTag encode(RegionPos pos) {
            List<Watershed> selected = pos.equals(center) ? watersheds : watersheds.stream()
                .filter(watershed -> watershed.allCells().stream().anyMatch(pos::contains)).toList();
            return RiverPreviewEncoding.encode(data, pos, paths.get(pos), selected);
        }

        public Result {
            paths = Map.copyOf(paths);
            watersheds = List.copyOf(watersheds);
        }
    }
}
