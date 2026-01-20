package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;

public class Path {
    private final List<CellPos> cells = new ArrayList<>();
    private final Set<RegionPos> allowedRegions = new HashSet<>();
    private final Set<CellPos> boundaryLandCells = new HashSet<>();
    private final Set<CellPos> visited = new HashSet<>();
    private boolean valid = true;

    public Path(CellPos source, Set<Region> regions) {
        this.cells.add(source);
        this.visited.add(source);

        for (Region region : regions) {
            this.allowedRegions.add(region.pos());
            for (Boundary boundary : region.boundaries()) {
                this.boundaryLandCells.addAll(boundary.cells());
            }
        }
    }

    public void trace() {
        Timer.Record record = Store.getTimer(Path.class, "trace").start();

        while (valid) {
            CellPos current = cells.get(cells.size() - 1);

            if (isTerminus(current)) {
                record.stop();
                return;
            }

            CellPos nearest = findNearestBoundaryCell(current);
            if (nearest == null) {
                valid = false;
                record.stop();
                return;
            }

            CellPos next = followFlow(current, nearest);
            if (next == null) {
                valid = false;
                record.stop();
                return;
            }

            Cell nextCell = CellCache.get().getOrCompute(next);
            if (nextCell.feature().equals(Feature.DIVIDE)) {
                valid = false;
                record.stop();
                return;
            }

            if (visited.contains(next)) {
                valid = false;
                record.stop();
                return;
            }
            visited.add(next);

            cells.add(next);
        }

        record.stop();
    }

    private CellPos findNearestBoundaryCell(CellPos from) {
        Timer.Record record = Store.getTimer(Path.class, "findNearestBoundaryCell").start();

        if (boundaryLandCells.isEmpty()) {
            record.stop();
            return null;
        }

        CellPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (CellPos boundaryCell : boundaryLandCells) {
            double dist = distance(from, boundaryCell);
            if (dist < nearestDistance) {
                nearestDistance = dist;
                nearest = boundaryCell;
            }
        }

        record.stop();
        return nearest;
    }

    private CellPos followFlow(CellPos current, CellPos nearestBoundary) {
        Timer.Record record = Store.getTimer(Path.class, "followFlow").start();

        Cell cell = CellCache.get().getOrCompute(current);
        double currentDistance = distance(current, nearestBoundary);
        int mergeThreshold = CommonConfig.get().mergeThreshold();

        if (currentDistance <= mergeThreshold) {
            CellPos aligned = followAlignedFlow(current, cell.flowDirections(), nearestBoundary, currentDistance);
            if (aligned != null) {
                record.stop();
                return aligned;
            }
        } else {
            for (FlowDirection dir : cell.flowDirections()) {
                CellPos next = current.relative(dir);

                if (!allowedRegions.contains(next.getRegion())) {
                    continue;
                }

                if (distance(next, nearestBoundary) < currentDistance) {
                    record.stop();
                    return next;
                }
            }
        }

        CellPos forced = forceTowardBoundary(current, nearestBoundary, currentDistance);
        record.stop();
        return forced;
    }

    private CellPos followAlignedFlow(CellPos current, List<FlowDirection> flowDirections,
                                       CellPos nearestBoundary, double currentDistance) {
        int dx = nearestBoundary.x() - current.x();
        int dz = nearestBoundary.z() - current.z();

        CellPos best = null;
        double bestAlignment = -Double.MAX_VALUE;

        for (FlowDirection dir : flowDirections) {
            CellPos next = current.relative(dir);

            if (!allowedRegions.contains(next.getRegion())) {
                continue;
            }

            if (distance(next, nearestBoundary) >= currentDistance) {
                continue;
            }

            double alignment = dir.dx * dx + dir.dz * dz;
            if (alignment > bestAlignment) {
                bestAlignment = alignment;
                best = next;
            }
        }

        return best;
    }

    private CellPos forceTowardBoundary(CellPos current, CellPos nearestBoundary, double currentDistance) {
        CellPos best = null;
        double bestDistance = currentDistance;

        for (FlowDirection dir : FlowDirection.D8) {
            CellPos next = current.relative(dir);

            if (!allowedRegions.contains(next.getRegion())) {
                continue;
            }

            double dist = distance(next, nearestBoundary);
            if (dist < bestDistance) {
                bestDistance = dist;
                best = next;
            }
        }

        return best;
    }

    private boolean isTerminus(CellPos pos) {
        return boundaryLandCells.contains(pos);
    }

    private static double distance(CellPos a, CellPos b) {
        int dx = a.x() - b.x();
        int dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public List<CellPos> cells() {
        return cells;
    }

    public boolean isValid() {
        return valid;
    }

    public CellPos terminus() {
        if (cells.isEmpty()) {
            return null;
        }
        return cells.get(cells.size() - 1);
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        ListTag cellList = new ListTag();
        for (CellPos pos : cells) {
            CompoundTag cellTag = new CompoundTag();
            cellTag.putLong("pos", pos.toLong());
            cellList.add(cellTag);
        }
        tag.put("cells", cellList);
        return tag;
    }

    public static List<CellPos> decode(CompoundTag tag) {
        List<CellPos> positions = new ArrayList<>();
        ListTag cellList = tag.getList("cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < cellList.size(); i++) {
            CompoundTag cellTag = cellList.getCompound(i);
            positions.add(new CellPos(cellTag.getLong("pos")));
        }
        return positions;
    }
}
