package org.sosly.rivertale.river;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.river.PreparedRiverData.CellData;

final class RiverPreviewEncoding {
    private RiverPreviewEncoding() {}

    static CompoundTag encode(RiverRegion data) {
        Region region = data.region();
        RegionPos pos = region.pos();
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.toLong());
        tag.putString("type", region.type().name());
        ListTag boundaries = new ListTag();
        for (Boundary boundary : region.boundaries()) {
            boundaries.add(boundary.encode());
        }
        tag.put("boundaries", boundaries);
        ListTag cells = new ListTag();
        for (CellPos cellPos : region.cells()) {
            CellData cell = data.cells().get(cellPos);
            CompoundTag entry = new CompoundTag();
            entry.putLong("pos", cellPos.toLong());
            entry.putString("feature", cell.feature().name());
            entry.putInt("y", cell.height());
            cells.add(entry);
        }
        tag.put("cells", cells);
        Set<CellPos> merged = encodeWatersheds(tag, data.cells(), data.watersheds());
        tag.put("paths", encodePaths(data.cells(), data.flowlines(), merged));
        return tag;
    }

    private static Set<CellPos> encodeWatersheds(CompoundTag tag, Map<CellPos, CellData> cells,
                                                List<Watershed> watersheds) {
        ListTag edges = new ListTag();
        ListTag outlets = new ListTag();
        Set<CellPos> merged = new HashSet<>();
        for (Watershed watershed : watersheds) {
            merged.addAll(watershed.allCells());
            for (CellPos from : watershed.allCells()) {
                CellPos to = watershed.downstream(from);
                CompoundTag entry = new CompoundTag();
                if (to == null) {
                    entry.putLong("pos", from.toLong());
                    entry.putInt("y", cells.get(from).height());
                    outlets.add(entry);
                    continue;
                }
                entry.putLong("from", from.toLong());
                entry.putLong("to", to.toLong());
                entry.putInt("fromY", cells.get(from).height());
                entry.putInt("toY", cells.get(to).height());
                edges.add(entry);
            }
        }
        tag.put("watershed", edges);
        tag.put("outlets", outlets);
        return merged;
    }

    private static ListTag encodePaths(Map<CellPos, CellData> cells, List<Flowline> paths, Set<CellPos> merged) {
        ListTag result = new ListTag();
        for (Flowline path : paths) {
            if (!path.isValid() || (!path.cells().isEmpty() && merged.contains(path.cells().get(0)))) {
                continue;
            }
            long[] positions = new long[path.cells().size()];
            int[] heights = new int[positions.length];
            for (int i = 0; i < positions.length; i++) {
                CellPos pos = path.cells().get(i);
                positions[i] = pos.toLong();
                heights[i] = cells.get(pos).height();
            }
            CompoundTag entry = new CompoundTag();
            entry.putLongArray("cells", positions);
            entry.putIntArray("heights", heights);
            result.add(entry);
        }
        return result;
    }
}
