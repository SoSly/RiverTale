package org.sosly.rivertale.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.rivertale.client.ClientCellCache;
import org.sosly.rivertale.worldgen.river.CellClassification;
import org.sosly.rivertale.worldgen.river.FlowDirection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class VisualizeCellsPacket {

    private final boolean enabled;
    private final List<CellData> cells;

    public VisualizeCellsPacket(boolean enabled, List<CellData> cells) {
        this.enabled = enabled;
        this.cells = cells != null ? cells : new ArrayList<>();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeInt(cells.size());

        for (CellData cell : cells) {
            buf.writeInt(cell.cellX);
            buf.writeInt(cell.cellZ);
            buf.writeEnum(cell.classification);
            buf.writeBoolean(cell.isBasin);

            buf.writeInt(cell.riverPaths.size());
            for (Map.Entry<FlowDirection, List<int[]>> entry : cell.riverPaths.entrySet()) {
                buf.writeEnum(entry.getKey());
                List<int[]> path = entry.getValue();
                buf.writeInt(path.size());
                for (int[] point : path) {
                    buf.writeInt(point[0]);
                    buf.writeInt(point[1]);
                }
            }
        }
    }

    public static VisualizeCellsPacket decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int cellCount = buf.readInt();
        List<CellData> cells = new ArrayList<>(cellCount);

        for (int i = 0; i < cellCount; i++) {
            int cellX = buf.readInt();
            int cellZ = buf.readInt();
            CellClassification classification = buf.readEnum(CellClassification.class);
            boolean isBasin = buf.readBoolean();

            Map<FlowDirection, List<int[]>> riverPaths = new HashMap<>();
            int pathCount = buf.readInt();
            for (int j = 0; j < pathCount; j++) {
                FlowDirection direction = buf.readEnum(FlowDirection.class);
                int pointCount = buf.readInt();
                List<int[]> path = new ArrayList<>(pointCount);
                for (int k = 0; k < pointCount; k++) {
                    int row = buf.readInt();
                    int col = buf.readInt();
                    path.add(new int[]{row, col});
                }
                riverPaths.put(direction, path);
            }

            cells.add(new CellData(cellX, cellZ, classification, isBasin, riverPaths));
        }

        return new VisualizeCellsPacket(enabled, cells);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                handleClient();
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private void handleClient() {
        ClientCellCache.setEnabled(enabled);
        if (enabled) {
            ClientCellCache.updateCells(cells);
        }
    }

    public static class CellData {
        public final int cellX;
        public final int cellZ;
        public final CellClassification classification;
        public final boolean isBasin;
        public final Map<FlowDirection, List<int[]>> riverPaths;

        public CellData(int cellX, int cellZ, CellClassification classification,
                        boolean isBasin, Map<FlowDirection, List<int[]>> riverPaths) {
            this.cellX = cellX;
            this.cellZ = cellZ;
            this.classification = classification;
            this.isBasin = isBasin;
            this.riverPaths = riverPaths != null ? riverPaths : new HashMap<>();
        }
    }
}
