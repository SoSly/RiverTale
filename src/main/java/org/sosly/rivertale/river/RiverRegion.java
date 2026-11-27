package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.river.PreparedRiverData.CellData;

public record RiverRegion(Region region, Map<CellPos, CellData> cells, List<Flowline> flowlines,
                          List<Watershed> watersheds) {
    public RiverRegion {
        cells = Map.copyOf(cells);
        flowlines = List.copyOf(flowlines);
        watersheds = List.copyOf(watersheds);
    }

    static RiverRegion snapshot(RegionPos pos, PreparedRiverData data, Map<CellPos, CellData> classified, List<Flowline> flowlines,
                                List<Watershed> watersheds) {
        Region region = data.region(pos);
        Map<CellPos, CellData> cells = new HashMap<>();
        for (CellPos cell : region.cells()) {
            CellData member = data.cell(cell);
            cells.put(cell, member);
            if (member.feature() != Feature.KETTLE && member.feature() != Feature.ENDORHEIC) {
                continue;
            }
            for (FlowDirection dir : FlowDirection.D8) {
                CellPos neighbor = cell.relative(dir);
                if (!pos.contains(neighbor)) {
                    CellData shore = data.cells().get(neighbor);
                    if (shore != null && shore.feature() == member.feature()) {
                        cells.put(neighbor, shore);
                    }
                }
            }
        }
        flowlines.forEach(path -> path.cells().forEach(cell -> cells.put(cell, data.cell(cell))));
        watersheds.forEach(watershed -> watershed.allCells().forEach(cell -> cells.put(cell, classified.get(cell))));
        return new RiverRegion(region, cells, flowlines, watersheds);
    }

    public Watershed watershedAt(CellPos pos) {
        for (Watershed watershed : watersheds) {
            if (watershed.contains(pos)) {
                return watershed;
            }
        }
        return null;
    }

    public CompoundTag encode() {
        return RiverPreviewEncoding.encode(this);
    }
}
