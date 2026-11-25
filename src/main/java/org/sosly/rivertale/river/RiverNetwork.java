package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.river.PreparedRiverData.CellData;
import org.sosly.rivertale.world.RiverShaping;

final class RiverNetwork {
    private RiverNetwork() {
    }

    static Map<RegionPos, RiverRegion> build(Set<RegionPos> roots, Runnable checkCancelled) {
        Timer.Record timer = Store.getAlwaysOnTimer(RiverNetwork.class, "build").start();
        try {
            return assemble(roots, RiverPreparation.prepare(roots, checkCancelled), checkCancelled);
        } finally {
            timer.stop();
        }
    }

    static Map<RegionPos, RiverRegion> assemble(Set<RegionPos> roots, PreparedRiverData data) {
        return assemble(roots, data, () -> { });
    }

    private static Map<RegionPos, RiverRegion> assemble(Set<RegionPos> roots, PreparedRiverData data, Runnable checkCancelled) {
        checkCancelled.run();
        FlowlinePreparation.Result traced = FlowlinePreparation.prepare(roots, new FlowlineTracer(data), checkCancelled);
        checkCancelled.run();
        List<Flowline> flowlines = WatershedPreparation.gather(traced);
        checkCancelled.run();
        List<Watershed> watersheds = new ArrayList<>();
        CommonConfig config = CommonConfig.get();
        for (Watershed watershed : WatershedBuilder.build(flowlines)) {
            checkCancelled.run();
            watersheds.add(watershed.withElevations(data, config.riseRunRatio())
                .withFlowAccumulation(config.sourceFlow(), config.flowPerCell()));
        }
        checkCancelled.run();
        Map<CellPos, CellData> classified = qualifyWatersheds(watersheds, data, checkCancelled);
        Map<RegionPos, List<Flowline>> pathsByRegion = index(roots, flowlines, Flowline::cells);
        Map<RegionPos, List<Watershed>> watershedsByRegion = index(roots, watersheds, Watershed::allCells);
        Map<RegionPos, RiverRegion> result = new HashMap<>();
        for (RegionPos pos : roots) {
            result.put(pos, RiverRegion.snapshot(pos, data, classified, pathsByRegion.getOrDefault(pos, List.of()),
                watershedsByRegion.getOrDefault(pos, List.of())));
        }
        return result;
    }

    private static Map<CellPos, CellData> qualifyWatersheds(List<Watershed> watersheds, PreparedRiverData data, Runnable checkCancelled) {
        Map<CellPos, CellData> classified = new HashMap<>();
        for (Watershed watershed : watersheds) {
            List<CellPos> cells = watershed.outletToSources();
            Collections.reverse(cells);
            for (CellPos pos : cells) {
                checkCancelled.run();
                CellData cell = data.cell(pos);
                if (cell.feature() != Feature.NONE) {
                    classified.put(pos, cell);
                    continue;
                }
                Feature feature = RiverShaping.qualify(pos, watershed).feature();
                classified.put(pos, new CellData(feature, cell.flowDirections(), cell.height()));
            }
        }
        return classified;
    }

    private static <T> Map<RegionPos, List<T>> index(Set<RegionPos> roots, List<T> features,
                                                     Function<T, Collection<CellPos>> cells) {
        Map<RegionPos, List<T>> result = new HashMap<>();
        for (T feature : features) {
            cells.apply(feature).stream().map(CellPos::getRegion).distinct().filter(roots::contains)
                .forEach(region -> result.computeIfAbsent(region, ignored -> new ArrayList<>()).add(feature));
        }
        return result;
    }
}
