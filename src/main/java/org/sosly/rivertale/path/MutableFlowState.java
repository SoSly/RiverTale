package org.sosly.rivertale.path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;

public class MutableFlowState {

    private final Grid flowGrid;
    private final Crossing[] crossings;
    private final double[] strengths;
    private final Direction primaryOutputDirection;
    private final Map<Direction, List<CellPos>> riverPaths;
    private final List<CellPos> originalTermini;
    private final List<CellPos> originalConfluences;
    private final Map<CellPos, Set<Direction>> confluenceUsage;

    public MutableFlowState(Flow flow) {
        this.flowGrid = flow.flowGrid();
        this.crossings = flow.crossings().clone();
        this.strengths = flow.crossingStrengths().clone();
        this.primaryOutputDirection = flow.primaryOutputDirection();
        this.riverPaths = new HashMap<>(flow.riverPaths());
        this.originalTermini = flow.terminusCells();
        this.originalConfluences = flow.confluenceCells();
        this.confluenceUsage = buildConfluenceUsage();
    }

    private Map<CellPos, Set<Direction>> buildConfluenceUsage() {
        Set<CellPos> confluenceSet = new HashSet<>(originalConfluences);
        Map<CellPos, Set<Direction>> usage = new HashMap<>();

        for (Map.Entry<Direction, List<CellPos>> entry : riverPaths.entrySet()) {
            Direction dir = entry.getKey();
            for (CellPos cell : entry.getValue()) {
                if (confluenceSet.contains(cell)) {
                    usage.computeIfAbsent(cell, k -> new HashSet<>()).add(dir);
                }
            }
        }

        return usage;
    }

    public List<CellPos> getPath(Direction dir) {
        return riverPaths.getOrDefault(dir, List.of());
    }

    public Set<Direction> getInputDirections() {
        return new HashSet<>(riverPaths.keySet());
    }

    public Set<CellPos> getConfluenceSet() {
        return new HashSet<>(originalConfluences);
    }

    public Direction getPrimaryOutputDirection() {
        return primaryOutputDirection;
    }

    public void removePath(Direction dir) {
        List<CellPos> path = riverPaths.remove(dir);
        if (path == null) {
            return;
        }

        Crossing crossing = crossings[dir.ordinal()];
        if (crossing != null) {
            crossing.invalidate();
        }
        strengths[dir.ordinal()] = 0;

        Set<CellPos> confluenceSet = new HashSet<>(originalConfluences);
        for (CellPos cell : path) {
            if (confluenceSet.contains(cell)) {
                Set<Direction> usage = confluenceUsage.get(cell);
                if (usage != null) {
                    usage.remove(dir);
                }
            }
        }
    }

    public Flow toFlow() {
        if (riverPaths.isEmpty()) {
            for (int i = 0; i < 4; i++) {
                if (crossings[i] != null) {
                    crossings[i].invalidate();
                }
                strengths[i] = 0;
            }

            return new Flow(
                flowGrid,
                crossings,
                strengths,
                Direction.NONE,
                List.of(),
                Map.of(),
                List.of()
            );
        }

        Set<CellPos> validCells = collectValidCells();
        List<CellPos> filteredTermini = filterByValidCells(originalTermini, validCells);
        List<CellPos> filteredConfluences = cleanupConfluences();

        return new Flow(
            flowGrid,
            crossings,
            strengths,
            primaryOutputDirection,
            filteredTermini,
            Map.copyOf(riverPaths),
            filteredConfluences
        );
    }

    private Set<CellPos> collectValidCells() {
        Set<CellPos> cells = new HashSet<>();
        for (List<CellPos> path : riverPaths.values()) {
            cells.addAll(path);
        }
        return cells;
    }

    private List<CellPos> filterByValidCells(List<CellPos> cells, Set<CellPos> validCells) {
        List<CellPos> filtered = new ArrayList<>();
        for (CellPos cell : cells) {
            if (validCells.contains(cell)) {
                filtered.add(cell);
            }
        }
        return filtered;
    }

    private List<CellPos> cleanupConfluences() {
        List<CellPos> cleaned = new ArrayList<>();
        for (CellPos cell : originalConfluences) {
            Set<Direction> usage = confluenceUsage.get(cell);
            if (usage != null && usage.size() >= 2) {
                cleaned.add(cell);
            }
        }
        return cleaned;
    }
}
