package org.sosly.rivertale.cell.feature;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public interface FeatureHandler {
    @Nullable Shape[][] shape(Cell cell, Watershed watershed, ChunkPos pos);
    boolean classify(Cell cell, @Nullable Watershed watershed);
    void fill();

    record PathInfo(double distance, boolean isEntrySegment, double t) {}

    default int[] getEdgePoint(Direction dir, int cellSize, int center) {
        return switch (dir) {
            case NORTH -> new int[]{center, 0};
            case SOUTH -> new int[]{center, cellSize - 1};
            case WEST -> new int[]{0, center};
            case EAST -> new int[]{cellSize - 1, center};
            case NORTHWEST -> new int[]{0, 0};
            case NORTHEAST -> new int[]{cellSize - 1, 0};
            case SOUTHWEST -> new int[]{0, cellSize - 1};
            case SOUTHEAST -> new int[]{cellSize - 1, cellSize - 1};
            case NONE -> new int[]{center, center};
        };
    }

    default Direction getDirection(CellPos from, CellPos to) {
        if (to == null) {
            return Direction.NONE;
        }
        int dx = to.x() - from.x();
        int dz = to.z() - from.z();

        for (Direction dir : Direction.D8) {
            if (dir.dx == dx && dir.dz == dz) {
                return dir;
            }
        }
        return Direction.NONE;
    }

    default Direction getEntryDirection(CellPos cellPos, Set<CellPos> upstreamSet) {
        if (upstreamSet.isEmpty()) {
            return Direction.NONE;
        }
        CellPos upstream = upstreamSet.iterator().next();
        return getDirection(cellPos, upstream);
    }

    default PathInfo getPathInfo(int x, int z, int[] entry, int[] exit, int centerX, int centerZ) {
        double d1 = distanceToSegment(x, z, entry[0], entry[1], centerX, centerZ);
        double d2 = distanceToSegment(x, z, centerX, centerZ, exit[0], exit[1]);

        if (d1 <= d2) {
            double t = projectOntoSegment(x, z, entry[0], entry[1], centerX, centerZ);
            return new PathInfo(d1, true, t);
        } else {
            double t = projectOntoSegment(x, z, centerX, centerZ, exit[0], exit[1]);
            return new PathInfo(d2, false, t);
        }
    }

    default double projectOntoSegment(int px, int pz, int x1, int z1, int x2, int z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double lengthSq = dx * dx + dz * dz;

        if (lengthSq == 0) {
            return 0;
        }

        return Math.max(0, Math.min(1, ((px - x1) * dx + (pz - z1) * dz) / lengthSq));
    }

    default int interpolateY(PathInfo info, int entryY, int centerY, int exitY) {
        if (info.isEntrySegment) {
            return (int) Math.round(entryY + (centerY - entryY) * info.t);
        } else {
            return (int) Math.round(centerY + (exitY - centerY) * info.t);
        }
    }

    default double distanceToSegment(int px, int pz, int x1, int z1, int x2, int z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double lengthSq = dx * dx + dz * dz;

        if (lengthSq == 0) {
            return Math.sqrt((px - x1) * (px - x1) + (pz - z1) * (pz - z1));
        }

        double t = Math.max(0, Math.min(1, ((px - x1) * dx + (pz - z1) * dz) / lengthSq));
        double projX = x1 + t * dx;
        double projZ = z1 + t * dz;

        return Math.sqrt((px - projX) * (px - projX) + (pz - projZ) * (pz - projZ));
    }
}
