package org.sosly.rivertale.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.sosly.rivertale.network.VisualizeCellsPacket.CellData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClientCellCache {

    private static boolean enabled = false;
    private static final Map<CellKey, CellData> CELLS = new HashMap<>();

    public static void setEnabled(boolean enabled) {
        ClientCellCache.enabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void updateCells(List<CellData> newCells) {
        CELLS.clear();
        for (CellData cell : newCells) {
            CELLS.put(new CellKey(cell.cellX, cell.cellZ), cell);
        }
    }

    public static List<CellData> getCells() {
        return new ArrayList<>(CELLS.values());
    }

    public static CellData getCell(int cellX, int cellZ) {
        return CELLS.get(new CellKey(cellX, cellZ));
    }

    public static void clear() {
        CELLS.clear();
    }

    private record CellKey(int x, int z) {}
}
