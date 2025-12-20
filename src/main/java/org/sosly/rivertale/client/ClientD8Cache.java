package org.sosly.rivertale.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.sosly.rivertale.network.VisualizeD8Packet.D8CellData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@OnlyIn(Dist.CLIENT)
public class ClientD8Cache {

    private static boolean enabled = false;
    private static final Map<CellKey, D8CellData> CELLS = new HashMap<>();

    public static void setEnabled(boolean enabled) {
        ClientD8Cache.enabled = enabled;
        if (!enabled) {
            clear();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void updateCells(List<D8CellData> newCells) {
        CELLS.clear();
        for (D8CellData cell : newCells) {
            CELLS.put(new CellKey(cell.cellX, cell.cellZ), cell);
        }
    }

    public static List<D8CellData> getCells() {
        return new ArrayList<>(CELLS.values());
    }

    public static D8CellData getCell(int cellX, int cellZ) {
        return CELLS.get(new CellKey(cellX, cellZ));
    }

    public static void clear() {
        CELLS.clear();
    }

    private record CellKey(int x, int z) {}
}
