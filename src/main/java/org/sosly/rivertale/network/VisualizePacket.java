package org.sosly.rivertale.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.client.Cache;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.path.Crossing;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.region.RegionType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class VisualizePacket {

    private final boolean enabled;
    private final List<D8RegionData> regions;

    public VisualizePacket(boolean enabled, List<D8RegionData> regions) {
        this.enabled = enabled;
        this.regions = regions != null ? regions : new ArrayList<>();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeInt(regions.size());

        for (D8RegionData region : regions) {
            buf.writeInt(region.regionX);
            buf.writeInt(region.regionZ);
            buf.writeEnum(region.featureRegionType);

            for (int i = 0; i < 4; i++) {
                Crossing crossing = region.crossings[i];
                buf.writeBoolean(crossing != null);
                if (crossing != null) {
                    buf.writeInt(crossing.source().x());
                    buf.writeInt(crossing.source().z());
                    buf.writeInt(crossing.destination().x());
                    buf.writeInt(crossing.destination().z());
                    buf.writeInt(crossing.slot());
                    buf.writeBoolean(crossing.isValid());
                }
            }
            buf.writeEnum(region.primaryOutputDirection);

            int gridSize = region.flowGrid.size();
            buf.writeInt(gridSize);
            for (int row = 0; row < gridSize; row++) {
                for (int col = 0; col < gridSize; col++) {
                    buf.writeEnum(region.flowGrid.getFlowAt(row, col));
                }
            }

            buf.writeInt(region.terminusCells.size());
            for (CellPos terminus : region.terminusCells) {
                buf.writeInt(terminus.row());
                buf.writeInt(terminus.col());
            }

            buf.writeInt(region.riverPaths.size());
            for (Map.Entry<Direction, List<CellPos>> entry : region.riverPaths.entrySet()) {
                buf.writeEnum(entry.getKey());
                buf.writeInt(entry.getValue().size());
                for (CellPos cell : entry.getValue()) {
                    buf.writeInt(cell.row());
                    buf.writeInt(cell.col());
                }
            }

            buf.writeInt(region.confluenceCells.size());
            for (CellPos confluence : region.confluenceCells) {
                buf.writeInt(confluence.row());
                buf.writeInt(confluence.col());
            }
        }
    }

    public static VisualizePacket decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int regionCount = buf.readInt();
        List<D8RegionData> regions = new ArrayList<>(regionCount);

        for (int i = 0; i < regionCount; i++) {
            int regionX = buf.readInt();
            int regionZ = buf.readInt();
            RegionPos regionPos = new RegionPos(regionX, regionZ);
            RegionType featureRegionType = buf.readEnum(RegionType.class);

            Crossing[] crossings = new Crossing[4];
            for (int j = 0; j < 4; j++) {
                boolean hasCrossing = buf.readBoolean();
                if (hasCrossing) {
                    RegionPos source = new RegionPos(buf.readInt(), buf.readInt());
                    RegionPos destination = new RegionPos(buf.readInt(), buf.readInt());
                    int slot = buf.readInt();
                    boolean valid = buf.readBoolean();
                    Crossing crossing = new Crossing(source, destination, slot);
                    if (!valid) {
                        crossing.invalidate();
                    }
                    crossings[j] = crossing;
                }
            }
            Direction primaryOutputDirection = buf.readEnum(Direction.class);

            int gridSize = buf.readInt();
            Grid flowGrid = Grid.create(regionPos);
            for (int row = 0; row < gridSize; row++) {
                for (int col = 0; col < gridSize; col++) {
                    flowGrid.get(row, col).setFlowDirection(buf.readEnum(Direction.class));
                }
            }

            int terminusCount = buf.readInt();
            List<CellPos> terminusCells = new ArrayList<>(terminusCount);
            for (int t = 0; t < terminusCount; t++) {
                int row = buf.readInt();
                int col = buf.readInt();
                terminusCells.add(CellPos.fromLocal(regionPos, row, col));
            }

            int pathCount = buf.readInt();
            Map<Direction, List<CellPos>> riverPaths = new HashMap<>();
            for (int p = 0; p < pathCount; p++) {
                Direction dir = buf.readEnum(Direction.class);
                int pathLength = buf.readInt();
                List<CellPos> path = new ArrayList<>(pathLength);
                for (int s = 0; s < pathLength; s++) {
                    int row = buf.readInt();
                    int col = buf.readInt();
                    path.add(CellPos.fromLocal(regionPos, row, col));
                }
                riverPaths.put(dir, path);
            }

            int confluenceCount = buf.readInt();
            List<CellPos> confluenceCells = new ArrayList<>(confluenceCount);
            for (int c = 0; c < confluenceCount; c++) {
                int row = buf.readInt();
                int col = buf.readInt();
                confluenceCells.add(CellPos.fromLocal(regionPos, row, col));
            }

            regions.add(new D8RegionData(regionX, regionZ, featureRegionType,
                crossings, primaryOutputDirection, flowGrid,
                terminusCells, riverPaths, confluenceCells));
        }

        return new VisualizePacket(enabled, regions);
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
        Cache.setEnabled(enabled);
        if (enabled) {
            Cache.updateRegions(regions);
        }
    }

    public static class D8RegionData {
        public final int regionX;
        public final int regionZ;
        public final RegionType featureRegionType;
        public final Crossing[] crossings;
        public final Direction primaryOutputDirection;
        public final Grid flowGrid;
        public final List<CellPos> terminusCells;
        public final Map<Direction, List<CellPos>> riverPaths;
        public final List<CellPos> confluenceCells;

        public D8RegionData(int regionX, int regionZ, RegionType featureRegionType,
                            Crossing[] crossings,
                            Direction primaryOutputDirection, Grid flowGrid,
                            List<CellPos> terminusCells, Map<Direction, List<CellPos>> riverPaths,
                            List<CellPos> confluenceCells) {
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.featureRegionType = featureRegionType;
            this.crossings = crossings;
            this.primaryOutputDirection = primaryOutputDirection;
            this.flowGrid = flowGrid;
            this.terminusCells = terminusCells != null ? terminusCells : new ArrayList<>();
            this.riverPaths = riverPaths != null ? riverPaths : new HashMap<>();
            this.confluenceCells = confluenceCells != null ? confluenceCells : new ArrayList<>();
        }
    }
}
