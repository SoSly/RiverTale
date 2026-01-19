package org.sosly.rivertale.region;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;

public class RegionExplorer {

    public static Set<Region> discover(BlockPos blockPos) {
        RegionPos regionPos = new RegionPos(blockPos);

        if (regionPos.containsOutOfBoundsCells()) {
            return Set.of();
        }

        Region currentRegion = RegionCache.get().getOrCompute(regionPos);
        RegionType type = currentRegion.type();

        if (type == RegionType.OCEANIC || type == RegionType.INLAND) {
            return Set.of();
        }

        Set<Region> workingSet = new HashSet<>();
        workingSet.add(currentRegion);

        for (FlowDirection dir : FlowDirection.D4) {
            RegionPos neighborPos = regionPos.relative(dir);
            if (neighborPos.containsOutOfBoundsCells()) {
                continue;
            }

            Region neighbor = RegionCache.get().getOrCompute(neighborPos);

            boolean shouldAdd = false;
            if (type == RegionType.COASTAL && neighbor.type() == RegionType.FLUVIAL) {
                shouldAdd = borderCellsFlowToward(neighbor, currentRegion, dir.opposite());
            } else if (type == RegionType.FLUVIAL && neighbor.type() == RegionType.COASTAL) {
                shouldAdd = borderCellsFlowToward(currentRegion, neighbor, dir);
            }

            if (!shouldAdd) {
                continue;
            }

            workingSet.add(neighbor);
        }

        return workingSet;
    }

    private static boolean borderCellsFlowToward(Region source, Region target, FlowDirection edgeDirection) {
        List<CellPos> borderCells = source.pos().getBorderCells(edgeDirection);
        CellCache cellCache = CellCache.get();

        for (CellPos cellPos : borderCells) {
            Cell cell = cellCache.getOrComputeWithFlow(cellPos);

            for (FlowDirection flowDir : cell.flowDirections()) {
                CellPos neighborPos = cellPos.relative(flowDir);
                if (!target.pos().contains(neighborPos)) {
                    continue;
                }
                return true;
            }
        }

        return false;
    }
}
