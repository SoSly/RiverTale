package org.sosly.rivertale.river;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
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
            OptionalInt cellY = assignedHeight(cellPos, data.watershedAt(cellPos));
            entry.putInt("y", cellY.orElse(cell.height()));
            if (cellY.isPresent()) {
                entry.putInt("averageY", cell.height());
            }
            cells.add(entry);
        }
        tag.put("cells", cells);
        Set<CellPos> merged = encodeWatersheds(tag, data.cells(), data.watersheds());
        tag.put("paths", encodePaths(data, merged));
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
                    entry.putInt("y", previewHeight(from, watershed, cells.get(from).height()));
                    outlets.add(entry);
                    continue;
                }
                entry.putLong("from", from.toLong());
                entry.putLong("to", to.toLong());
                entry.putInt("fromY", previewHeight(from, watershed, cells.get(from).height()));
                entry.putInt("toY", previewHeight(to, watershed, cells.get(to).height()));
                edges.add(entry);
            }
        }
        tag.put("watershed", edges);
        tag.put("outlets", outlets);
        return merged;
    }

    private static ListTag encodePaths(RiverRegion data, Set<CellPos> merged) {
        ListTag result = new ListTag();
        for (Flowline path : data.flowlines()) {
            if (!path.isValid() || (!path.cells().isEmpty() && merged.contains(path.cells().get(0)))) {
                continue;
            }
            long[] positions = new long[path.cells().size()];
            int[] heights = new int[positions.length];
            for (int i = 0; i < positions.length; i++) {
                CellPos pos = path.cells().get(i);
                positions[i] = pos.toLong();
                heights[i] = previewHeight(pos, data.watershedAt(pos), data.cells().get(pos).height());
            }
            CompoundTag entry = new CompoundTag();
            entry.putLongArray("cells", positions);
            entry.putIntArray("heights", heights);
            result.add(entry);
        }
        return result;
    }

    private static int previewHeight(CellPos pos, Watershed watershed, int sampledHeight) {
        return assignedHeight(pos, watershed).orElse(sampledHeight);
    }

    private static OptionalInt assignedHeight(CellPos pos, Watershed watershed) {
        return watershed == null ? OptionalInt.empty() : watershed.cellY(pos);
    }
}
