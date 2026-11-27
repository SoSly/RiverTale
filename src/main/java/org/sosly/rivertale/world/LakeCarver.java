package org.sosly.rivertale.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.PreparedRiverData.CellData;
import org.sosly.rivertale.terrain.CarveProposal;

final class LakeCarver {
    private LakeCarver() {}

    static List<CarveProposal> carve(CellPos pos, CellData lake, Map<CellPos, CellData> cells, ChunkPos chunk) {
        int extra = CommonConfig.get().maxLakeCells();
        if (extra == 0) {
            return List.of();
        }
        int size = CommonConfig.get().cellBlocks();
        int cx = pos.getMiddleBlockX();
        int cz = pos.getMiddleBlockZ();
        double surface = (double) lake.height() + lake.cell().overFill();
        double center = surface - lake.height() + extra;
        List<CarveProposal> proposals = new ArrayList<>();
        for (int dx : new int[]{-1, 1}) {
            for (int dz : new int[]{-1, 1}) {
                int minX = Math.max(chunk.getMinBlockX(), dx < 0 ? pos.getMinBlockX() : cx);
                int maxX = Math.min(chunk.getMaxBlockX(), dx < 0 ? cx - 1 : pos.getMaxBlockX() - 1);
                int minZ = Math.max(chunk.getMinBlockZ(), dz < 0 ? pos.getMinBlockZ() : cz);
                int maxZ = Math.min(chunk.getMaxBlockZ(), dz < 0 ? cz - 1 : pos.getMaxBlockZ() - 1);
                if (minX > maxX || minZ > maxZ) {
                    continue;
                }
                double eastWest = depth(cells.get(new CellPos(pos.x() + dx, pos.z())), lake.feature(), surface, extra);
                double northSouth = depth(cells.get(new CellPos(pos.x(), pos.z() + dz)), lake.feature(), surface, extra);
                double diagonal = depth(cells.get(new CellPos(pos.x() + dx, pos.z() + dz)), lake.feature(), surface, extra);
                Bank bank = new Bank(center, eastWest, northSouth, diagonal);
                for (int x = minX; x <= maxX; x++) {
                    double u = (x + 0.5 - cx) * 2 / size;
                    for (int z = minZ; z <= maxZ; z++) {
                        double v = (z + 0.5 - cz) * 2 / size;
                        double cut = bank.depthAt(u * u, v * v);
                        if (cut > 0) {
                            proposals.add(new CarveProposal(x, z, surface - cut, Math.min(1, cut)));
                        }
                    }
                }
            }
        }
        return proposals;
    }

    private static double depth(CellData neighbor, Feature feature, double surface, int extra) {
        if (neighbor == null || neighbor.feature() != feature || (double) neighbor.height() + neighbor.cell().overFill() != surface) {
            return 0;
        }
        return surface - neighbor.height() + extra;
    }

    private record Bank(double center, double xNeighbor, double zNeighbor, double diagonal) {
        double depthAt(double u, double v) {
            if (xNeighbor == 0 && zNeighbor == 0 && diagonal == 0) {
                return center * Math.max(0, 1 - u - v);
            }
            double xEdge = xNeighbor == 0 ? 0 : (center + xNeighbor) / 2;
            double zEdge = zNeighbor == 0 ? 0 : (center + zNeighbor) / 2;
            int members = 1 + (xNeighbor > 0 ? 1 : 0) + (zNeighbor > 0 ? 1 : 0) + (diagonal > 0 ? 1 : 0);
            double corner = diagonal > 0 || members >= 3 ? (center + xNeighbor + zNeighbor + diagonal) / members : 0;
            return (1 - v) * ((1 - u) * center + u * xEdge) + v * ((1 - u) * zEdge + u * corner);
        }
    }
}
