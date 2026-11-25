package org.sosly.rivertale.river;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.sosly.rivertale.config.CommonConfig;
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
        tag.put("cells", encodeCells(data));
        Set<CellPos> merged = encodeWatersheds(tag, data.cells(), data.watersheds());
        tag.put("paths", encodePaths(data, merged));
        return tag;
    }

    private static CompoundTag encodeCells(RiverRegion data) {
        List<CellPos> positions = data.region().cells();
        byte[] features = new byte[positions.size()];
        int[] heights = new int[positions.size()];
        IntArrayList averageYByIndex = new IntArrayList();
        LongArrayList flowByIndex = new LongArrayList();
        for (int i = 0; i < positions.size(); i++) {
            CellPos pos = positions.get(i);
            CellData cell = data.cells().get(pos);
            Watershed watershed = data.watershedAt(pos);
            OptionalInt cellY = assignedHeight(pos, watershed);
            features[i] = (byte) cell.feature().ordinal();
            heights[i] = cellY.orElse(cell.height());
            if (cellY.isPresent()) {
                averageYByIndex.add(i);
                averageYByIndex.add(cell.height());
            }
            OptionalDouble flow = watershed == null ? OptionalDouble.empty() : watershed.flow(pos);
            if (flow.isPresent()) {
                flowByIndex.add(i);
                flowByIndex.add(Double.doubleToRawLongBits(flow.getAsDouble()));
            }
        }
        CompoundTag cells = new CompoundTag();
        cells.putInt("side", CommonConfig.get().cellsPerRegion());
        cells.putByteArray("features", features);
        cells.putIntArray("heights", heights);
        cells.putIntArray("averageYByIndex", averageYByIndex.toIntArray());
        cells.putLongArray("flowByIndex", flowByIndex.toLongArray());
        return cells;
    }

    private static Set<CellPos> encodeWatersheds(CompoundTag tag, Map<CellPos, CellData> cells,
                                                List<Watershed> watersheds) {
        LongArrayList edgePositions = new LongArrayList();
        IntArrayList edgeHeights = new IntArrayList();
        ListTag outlets = new ListTag();
        Set<CellPos> merged = new HashSet<>();
        for (Watershed watershed : watersheds) {
            merged.addAll(watershed.allCells());
            for (CellPos from : watershed.allCells()) {
                CellPos to = watershed.downstream(from);
                int fromY = previewHeight(from, watershed, cells.get(from).height());
                if (to == null) {
                    CompoundTag outlet = new CompoundTag();
                    outlet.putLong("pos", from.toLong());
                    outlet.putInt("y", fromY);
                    outlets.add(outlet);
                    continue;
                }
                edgePositions.add(from.toLong());
                edgePositions.add(to.toLong());
                edgeHeights.add(fromY);
                edgeHeights.add(previewHeight(to, watershed, cells.get(to).height()));
            }
        }
        CompoundTag edges = new CompoundTag();
        edges.putLongArray("positions", edgePositions.toLongArray());
        edges.putIntArray("heights", edgeHeights.toIntArray());
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
