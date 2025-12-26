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
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.terrain.OceanBoundary;

public class Path {
    private static final double DISTANCE_TOLERANCE = 0.5;

    private final List<Cell> cells = new ArrayList<>();
    private final Set<RegionPos> allowedRegions = new HashSet<>();
    private final Set<CellPos> oceanCells = new HashSet<>();
    private final Set<CellPos> visited = new HashSet<>();
    private boolean valid = true;

    public Path(Cell source, Set<Region> regions) {
        this.cells.add(source);
        this.visited.add(source.pos());

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
            Cell current = cells.get(cells.size() - 1);

            if (isTerminus(current)) {
                record.stop();
                return;
            }

            CellPos nearest = findNearestOceanCell(current.pos());
            if (nearest == null) {
                valid = false;
                record.stop();
                return;
            }

            CellPos next = followFlow(current, nearest);
            if (next == null) {
                record.stop();
                return;
            }

            if (visited.contains(next)) {
                valid = false;
                record.stop();
                return;
            }
            visited.add(next);

            Cell nextCell = CellCache.get().getOrCompute(next);
            cells.add(nextCell);
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

    private CellPos followFlow(Cell current, CellPos nearestOcean) {
        Timer.Record record = Store.getTimer(Path.class, "followFlow").start();

        Direction flow = current.flowDirection();
        if (flow == Direction.NONE) {
            CellPos next = stepTowardOcean(current.pos(), nearestOcean);
            record.stop();
            return next;
        }

        CellPos next = current.pos().relative(flow);
        double currentDistance = distance(current.pos(), nearestOcean);
        double nextDistance = distance(next, nearestOcean);

        if (nextDistance < currentDistance) {
            record.stop();
            return next;
        }

        if (!allowedRegions.contains(next.getRegion())) {
            valid = false;
            record.stop();
            return null;
        }

        if (nextDistance > currentDistance + DISTANCE_TOLERANCE) {
            valid = false;
            record.stop();
            return null;
        }

        record.stop();
        return next;
    }

    private CellPos stepTowardOcean(CellPos from, CellPos ocean) {
        int dx = ocean.x() - from.x();
        int dz = ocean.z() - from.z();

        if (Math.abs(dx) >= Math.abs(dz)) {
            return new CellPos(from.x() + Integer.signum(dx), from.z());
        }

        return new CellPos(from.x(), from.z() + Integer.signum(dz));
    }

    private boolean isTerminus(Cell cell) {
        return oceanCells.contains(cell.pos());
    }

    private static double distance(CellPos a, CellPos b) {
        int dx = a.x() - b.x();
        int dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    public List<Cell> cells() {
        return cells;
    }

    public boolean isValid() {
        return valid;
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        ListTag cellList = new ListTag();
        for (Cell cell : cells) {
            CompoundTag cellTag = new CompoundTag();
            cellTag.putLong("pos", cell.pos().toLong());
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
