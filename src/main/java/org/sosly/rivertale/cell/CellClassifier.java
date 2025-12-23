package org.sosly.rivertale.cell;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;

public class CellClassifier {

    public static CellType classify(Cell cell, Region region, Map<Direction, List<CellPos>> paths, RegionType terrain) {
        int row = cell.row();
        int col = cell.col();

        if (!isCellOnRiverPath(paths, row, col)) {
            return null;
        }

        int inletCount = countInlets(paths, row, col);

        if (isTerminusCell(region, paths, row, col, terrain)) {
            return CellType.TERMINUS;
        }
        if (isSourceCell(paths, row, col, inletCount)) {
            return CellType.SOURCE;
        }
        if (inletCount >= 2) {
            return CellType.JUNCTION;
        }
        return CellType.COURSE;
    }

    private static int countInlets(Map<Direction, List<CellPos>> paths, int row, int col) {
        Set<CellPos> uniqueInlets = new HashSet<>();
        for (List<CellPos> path : paths.values()) {
            for (int i = 1; i < path.size(); i++) {
                CellPos current = path.get(i);
                if (current.row() == row && current.col() == col) {
                    CellPos previous = path.get(i - 1);
                    uniqueInlets.add(previous);
                }
            }
        }
        return uniqueInlets.size();
    }

    private static boolean isCellOnRiverPath(Map<Direction, List<CellPos>> paths, int row, int col) {
        for (List<CellPos> path : paths.values()) {
            for (CellPos point : path) {
                if (point.row() == row && point.col() == col) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isTerminusCell(Region region, Map<Direction, List<CellPos>> paths, int row, int col, RegionType terrain) {
        boolean isShore = terrain == RegionType.SHORE;
        boolean isBasin = region.getPrimaryOutput() == Direction.NONE
            && region.getSecondaryOutputs().isEmpty();

        if (!isShore && !isBasin) {
            return false;
        }

        for (List<CellPos> path : paths.values()) {
            if (path.isEmpty()) {
                continue;
            }
            CellPos lastPoint = path.get(path.size() - 1);
            if (lastPoint.row() == row && lastPoint.col() == col) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSourceCell(Map<Direction, List<CellPos>> paths, int row, int col, int inletCount) {
        if (inletCount > 0) {
            return false;
        }

        for (Map.Entry<Direction, List<CellPos>> entry : paths.entrySet()) {
            List<CellPos> path = entry.getValue();
            if (path.isEmpty()) {
                continue;
            }
            CellPos firstPoint = path.get(0);
            if (firstPoint.row() == row && firstPoint.col() == col) {
                return true;
            }
        }
        return false;
    }
}
