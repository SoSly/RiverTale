package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import org.sosly.rivertale.core.CellPos;
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
        region.cells().forEach(cell -> cells.put(cell, data.cell(cell)));
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
