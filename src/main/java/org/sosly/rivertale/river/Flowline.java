package org.sosly.rivertale.river;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.core.CellPos;

public record Flowline(List<CellPos> cells, @Nullable CellPos terminus, boolean isValid) {
    public Flowline {
        cells = List.copyOf(cells);
    }

    public double lengthBlocks(int cellBlocks) {
        double length = 0;
        for (int i = 1; i < cells.size(); i++) {
            CellPos previous = cells.get(i - 1);
            CellPos current = cells.get(i);
            long dx = (long) current.x() - previous.x();
            long dz = (long) current.z() - previous.z();
            length += Math.hypot(dx, dz);
        }
        return length * cellBlocks;
    }

    public CompoundTag encode(CellCache cache) {
        long[] positions = new long[cells.size()];
        int[] heights = new int[cells.size()];
        for (int i = 0; i < cells.size(); i++) {
            CellPos pos = cells.get(i);
            positions[i] = pos.toLong();
            heights[i] = cache.getOrCompute(pos).averageEstimatedTerrainHeight();
        }

        CompoundTag tag = new CompoundTag();
        tag.putLongArray("cells", positions);
        tag.putIntArray("heights", heights);
        return tag;
    }
}
