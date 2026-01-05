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
    int DEFAULT_WIDTH = 16;
    int DEFAULT_DEPTH = 4;
    double CURVE_K = 2 * Math.acos(-0.2);
    double BANK_PEAK_RATIO = Math.PI / CURVE_K;
    double BLEND_DOWN_RATE = 3.0;
    double BLEND_UP_RATE = 1.5;
    double PARALLEL_DECAY_DISTANCE = 2.0;
    double PARALLEL_GRACE_ZONE = 4.0;  // No attenuation within this distance of path endpoints

    @Nullable Shape[][] shape(Cell cell, Watershed watershed, ChunkPos pos);
    boolean classify(Cell cell, @Nullable Watershed watershed);
    void fill();

    record PathInfo(double distance, boolean isEntrySegment, double t) {}

    record ProfileResult(int surfaceY, boolean isRiverbed, Integer waterY, double weight) {}

    default int bankPeakDistance(int width) {
        return (int) Math.ceil(BANK_PEAK_RATIO * width);
    }

    default int bankPeakHeight(int depth) {
        return (int) Math.round(2.0 * depth / 3.0);
    }

    default int riverbedEdge() {
        return riverbedEdge(DEFAULT_WIDTH, DEFAULT_DEPTH);
    }

    default int riverbedEdge(int width, int depth) {
        return bankPeakDistance(width);
    }

    default ProfileResult calculateProfile(double distance, int waterY, int vanillaY) {
        return calculateProfile(distance, waterY, vanillaY, DEFAULT_WIDTH, DEFAULT_DEPTH);
    }

    default ProfileResult calculateProfile(double distance, int waterY, int vanillaY, int width, int depth) {
        return calculateProfile(distance, waterY, vanillaY, width, depth, depth);
    }

    default ProfileResult calculateProfile(double distance, int waterY, int vanillaY, int width, int depth, int embankmentThreshold) {
        int halfWidth = width / 2;
        int bankPeakDist = bankPeakDistance(width);
        int bankPeakY = waterY + bankPeakHeight(depth);
        boolean needsBank = vanillaY <= waterY + embankmentThreshold;

        if (needsBank) {
            if (distance <= bankPeakDist) {
                double y = -(5.0 * depth / 6.0) * Math.cos(CURVE_K * distance / width) - (depth / 6.0);
                int roundedY = y > 0 ? (int) Math.ceil(y) : (int) Math.floor(y);
                int surfaceY = waterY + roundedY;
                boolean isRiverbed = surfaceY < waterY;
                Integer fillWaterY = isRiverbed ? waterY : null;
                return new ProfileResult(surfaceY, isRiverbed, fillWaterY, 1.0);
            }

            double blendDistance = distance - bankPeakDist;
            int targetY = (int) (bankPeakY - blendDistance / BLEND_DOWN_RATE);
            if (targetY <= vanillaY) {
                return null;
            }
            double totalBlendDist = (bankPeakY - vanillaY) * BLEND_DOWN_RATE;
            double weight = Math.max(0.0, Math.min(1.0, 1.0 - (blendDistance / totalBlendDist)));
            return new ProfileResult(targetY, false, null, weight);
        }

        if (distance <= halfWidth) {
            double y = -(5.0 * depth / 6.0) * Math.cos(CURVE_K * distance / width) - (depth / 6.0);
            int roundedY = y > 0 ? (int) Math.ceil(y) : (int) Math.floor(y);
            int surfaceY = waterY + roundedY;
            return new ProfileResult(surfaceY, true, waterY, 1.0);
        }

        double carveDistance = distance - halfWidth;
        int targetY = waterY + (int) (carveDistance / BLEND_UP_RATE);
        if (targetY >= vanillaY) {
            return null;
        }
        double totalCarveDist = (vanillaY - waterY) * BLEND_UP_RATE;
        double weight = Math.max(0.0, Math.min(1.0, 1.0 - (carveDistance / totalCarveDist)));
        return new ProfileResult(targetY, false, null, weight);
    }

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

    default double projectOntoSegmentUnclamped(int px, int pz, int x1, int z1, int x2, int z2) {
        double dx = x2 - x1;
        double dz = z2 - z1;
        double lengthSq = dx * dx + dz * dz;

        if (lengthSq == 0) {
            return 0;
        }

        return ((px - x1) * dx + (pz - z1) * dz) / lengthSq;
    }

    default int interpolateY(PathInfo info, int entryY, int centerY, int exitY) {
        return (int) Math.round(interpolateYExact(info, entryY, centerY, exitY));
    }

    default double interpolateYExact(PathInfo info, int entryY, int centerY, int exitY) {
        if (info.isEntrySegment) {
            return entryY + (centerY - entryY) * info.t;
        } else {
            return centerY + (exitY - centerY) * info.t;
        }
    }

    record FlowInfo(int waterY, Integer flowLevel) {}

    default FlowInfo computeFlowInfo(PathInfo info, int entryY, int centerY, int exitY, double segmentLength) {
        double exactY = interpolateYExact(info, entryY, centerY, exitY);
        int waterY = (int) Math.floor(exactY);

        int segmentStartY, segmentEndY;
        if (info.isEntrySegment()) {
            segmentStartY = entryY;
            segmentEndY = centerY;
        } else {
            segmentStartY = centerY;
            segmentEndY = exitY;
        }

        if (segmentEndY >= segmentStartY) {
            return new FlowInfo(waterY, null);
        }

        double dropY = waterY + 1.0;
        if (dropY > segmentStartY || dropY <= segmentEndY) {
            return new FlowInfo(waterY, null);
        }

        double tDrop = (dropY - segmentStartY) / (segmentEndY - segmentStartY);
        double blocksFromDrop = (info.t() - tDrop) * segmentLength;

        if (blocksFromDrop < 0.5 || blocksFromDrop > 7.5) {
            return new FlowInfo(waterY, null);
        }

        int flowLevel = (int) Math.round(blocksFromDrop);
        return new FlowInfo(waterY, flowLevel);
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
