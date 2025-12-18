package org.sosly.rivertale.worldgen.river;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class RiverCellSavedData extends SavedData {

    private static final String DATA_NAME = "rivertale_cells";
    private static final String TAG_CELLS = "cells";

    private final ConcurrentHashMap<RiverCellKey, RiverCell> cells;

    private RiverCellSavedData(ConcurrentHashMap<RiverCellKey, RiverCell> cells) {
        this.cells = cells;
    }

    private RiverCellSavedData() {
        this(new ConcurrentHashMap<>());
    }

    public static RiverCellSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
            RiverCellSavedData::load,
            RiverCellSavedData::new,
            DATA_NAME
        );
    }

    public static RiverCellSavedData load(CompoundTag tag) {
        ConcurrentHashMap<RiverCellKey, RiverCell> cells = new ConcurrentHashMap<>();

        CompoundTag cellsTag = tag.getCompound(TAG_CELLS);
        for (String key : cellsTag.getAllKeys()) {
            CompoundTag cellTag = cellsTag.getCompound(key);
            RiverCell cell = RiverCell.load(cellTag);
            cells.put(cell.getKey(), cell);
        }

        return new RiverCellSavedData(cells);
    }

    public RiverCell getOrCompute(RiverCellKey key, Function<RiverCellKey, RiverCell> computeFunction) {
        return cells.computeIfAbsent(key, k -> {
            RiverCell cell = computeFunction.apply(k);
            setDirty();
            return cell;
        });
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag cellsTag = new CompoundTag();

        for (RiverCell cell : cells.values()) {
            String key = cell.getKey().cellX() + "_" + cell.getKey().cellZ();
            cellsTag.put(key, cell.save());
        }

        tag.put(TAG_CELLS, cellsTag);
        return tag;
    }
}
