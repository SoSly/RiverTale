package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.terrain.OceanBoundary;

public class Path {
    private final List<CellPos> cells = new ArrayList<>();
    private final Set<RegionPos> allowedRegions = new HashSet<>();
    private final Set<CellPos> oceanCells = new HashSet<>();
    private final Set<CellPos> visited = new HashSet<>();
    private boolean valid = true;

    public Path(CellPos source, Set<Region> regions) {
        this.cells.add(source);
        this.visited.add(source);

        for (Region region : regions) {
            this.allowedRegions.add(region.pos());
            for (OceanBoundary boundary : region.boundaries()) {
                int blockX = boundary.land().getMinBlockX();
                int blockZ = boundary.land().getMinBlockZ();
                this.oceanCells.add(new CellPos(new BlockPos(blockX, 0, blockZ)));
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

            CellPos nearest = findNearestOceanCell(current);
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

    private CellPos findNearestOceanCell(CellPos from) {
        Timer.Record record = Store.getTimer(Path.class, "findNearestOceanCell").start();

        if (oceanCells.isEmpty()) {
            record.stop();
            return null;
        }

        CellPos nearest = null;
        double nearestDistance = Double.MAX_VALUE;

        for (CellPos ocean : oceanCells) {
            double dist = distance(from, ocean);
            if (dist < nearestDistance) {
                nearestDistance = dist;
                nearest = ocean;
            }
        }

        record.stop();
        return nearest;
    }

    private CellPos followFlow(CellPos current, CellPos nearestOcean) {
        Timer.Record record = Store.getTimer(Path.class, "followFlow").start();

        Cell cell = CellCache.get().getOrCompute(current);
        double currentDistance = distance(current, nearestOcean);
        int mergeThreshold = CommonConfig.get().mergeThreshold();

        if (currentDistance <= mergeThreshold) {
            CellPos aligned = followAlignedFlow(current, cell.flowDirections(), nearestOcean, currentDistance);
            if (aligned != null) {
                record.stop();
                return aligned;
            }
        } else {
            for (Direction dir : cell.flowDirections()) {
                CellPos next = current.relative(dir);

                if (!allowedRegions.contains(next.getRegion())) {
                    continue;
                }

                if (distance(next, nearestOcean) < currentDistance) {
                    record.stop();
                    return next;
                }
            }
        }

        CellPos forced = forceTowardOcean(current, nearestOcean, currentDistance);
        record.stop();
        return forced;
    }

    private CellPos followAlignedFlow(CellPos current, List<Direction> flowDirections,
                                       CellPos nearestOcean, double currentDistance) {
        int dx = nearestOcean.x() - current.x();
        int dz = nearestOcean.z() - current.z();

        CellPos best = null;
        double bestAlignment = -Double.MAX_VALUE;

        for (Direction dir : flowDirections) {
            CellPos next = current.relative(dir);

            if (!allowedRegions.contains(next.getRegion())) {
                continue;
            }

            if (distance(next, nearestOcean) >= currentDistance) {
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

    private CellPos forceTowardOcean(CellPos current, CellPos nearestOcean, double currentDistance) {
        CellPos best = null;
        double bestDistance = currentDistance;

        for (Direction dir : Direction.D8) {
            CellPos next = current.relative(dir);

            if (!allowedRegions.contains(next.getRegion())) {
                continue;
            }

            double dist = distance(next, nearestOcean);
            if (dist < bestDistance) {
                bestDistance = dist;
                best = next;
            }
        }

        return best;
    }

    private boolean isTerminus(CellPos pos) {
        return oceanCells.contains(pos);
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
