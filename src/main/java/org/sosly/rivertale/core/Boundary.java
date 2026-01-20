package org.sosly.rivertale.core;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public record Boundary(List<CellPos> cells, BoundaryType type) {

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putString("type", type.name());
        ListTag cellList = new ListTag();
        for (CellPos cell : cells) {
            CompoundTag cellTag = new CompoundTag();
            cellTag.putLong("pos", cell.toLong());
            cellList.add(cellTag);
        }
        tag.put("cells", cellList);
        return tag;
    }

    public static Boundary decode(CompoundTag tag) {
        BoundaryType type = BoundaryType.valueOf(tag.getString("type"));
        List<CellPos> cells = new ArrayList<>();
        ListTag cellList = tag.getList("cells", Tag.TAG_COMPOUND);
        for (int i = 0; i < cellList.size(); i++) {
            cells.add(new CellPos(cellList.getCompound(i).getLong("pos")));
        }
        return new Boundary(cells, type);
    }
}
