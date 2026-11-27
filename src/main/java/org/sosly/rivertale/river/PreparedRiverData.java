package org.sosly.rivertale.river;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;

public record PreparedRiverData(
    Map<RegionPos, Region> regions,
    Map<CellPos, CellData> cells,
    Map<RegionPos, Routing> routing,
    Map<CellPos, List<Step>> moves,
    Map<CellPos, List<CellPos>> incoming,
    Map<RegionPos, List<CellPos>> sources,
    int awayStepAllowance,
    int seaLevel,
    Map<LakePlan.Inlet, LakePlan> lakes,
    Map<CellPos, List<Step>> seaCuts
) {
    public PreparedRiverData(Map<RegionPos, Region> regions, Map<CellPos, CellData> cells, Map<RegionPos, Routing> routing,
                             Map<CellPos, List<Step>> moves, Map<CellPos, List<CellPos>> incoming,
                             Map<RegionPos, List<CellPos>> sources, int awayStepAllowance, int seaLevel) {
        this(regions, cells, routing, moves, incoming, sources, awayStepAllowance, seaLevel, Map.of());
    }

    public PreparedRiverData(Map<RegionPos, Region> regions, Map<CellPos, CellData> cells, Map<RegionPos, Routing> routing,
                             Map<CellPos, List<Step>> moves, Map<CellPos, List<CellPos>> incoming,
                             Map<RegionPos, List<CellPos>> sources, int awayStepAllowance, int seaLevel,
                             Map<LakePlan.Inlet, LakePlan> lakes) {
        this(regions, cells, routing, moves, incoming, sources, awayStepAllowance, seaLevel, lakes, Map.of());
    }

    public PreparedRiverData {
        seaCuts = immutableLists(seaCuts);
        lakes = Map.copyOf(lakes);
        regions = Map.copyOf(regions);
        cells = Map.copyOf(cells);
        routing = Map.copyOf(routing);
        moves = immutableLists(moves);
        incoming = immutableLists(incoming);
        sources = immutableLists(sources);
    }

    Region region(RegionPos pos) {
        return require(regions, pos, "region");
    }

    CellData cell(CellPos pos) {
        return require(cells, pos, "cell");
    }

    Routing routing(RegionPos pos) {
        return require(routing, pos, "routing");
    }

    List<Step> steps(CellPos pos) {
        return require(moves, pos, "moves");
    }

    List<CellPos> predecessors(CellPos pos) {
        if (pos.getRegion().containsOutOfBoundsCells()) {
            return List.of();
        }
        return require(incoming, pos, "predecessors");
    }

    List<CellPos> sources(RegionPos pos) {
        return require(sources, pos, "sources");
    }

    private static <K, V> Map<K, List<V>> immutableLists(Map<K, List<V>> values) {
        Map<K, List<V>> copy = new HashMap<>();
        values.forEach((key, value) -> copy.put(key, List.copyOf(value)));
        return Map.copyOf(copy);
    }

    private static <K, V> V require(Map<K, V> values, K key, String kind) {
        V value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("Incomplete river preparation: missing " + kind + " at " + key);
        }
        return value;
    }

    public record CellData(Cell cell, int height) {
        public Feature feature() {
            return cell.feature();
        }

        public List<FlowDirection> flowDirections() {
            return cell.flowDirections();
        }
    }

    public record Routing(Set<RegionPos> allowedRegions, List<CellPos> termini, Set<CellPos> terminusCells,
                          Set<CellPos> waterCells, Set<CellPos> oceanOutlets) {
        public Routing(Set<RegionPos> allowedRegions, List<CellPos> termini, Set<CellPos> terminusCells, Set<CellPos> waterCells) {
            this(allowedRegions, termini, terminusCells, waterCells, Set.of());
        }

        public Routing {
            oceanOutlets = Set.copyOf(oceanOutlets);
            allowedRegions = Set.copyOf(allowedRegions);
            termini = List.copyOf(termini);
            terminusCells = Set.copyOf(terminusCells);
            waterCells = Set.copyOf(waterCells);
        }

        boolean allows(CellPos pos) {
            return allowedRegions.contains(pos.getRegion()) && !waterCells.contains(pos);
        }
    }

    public record Step(CellPos pos, long nudgingRise, boolean approaching, boolean wall) {
        public Step(CellPos pos, long nudgingRise, boolean approaching) {
            this(pos, nudgingRise, approaching, false);
        }
    }
}
