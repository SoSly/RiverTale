package org.sosly.rivertale.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.rivertale.client.ClientD8Cache;
import org.sosly.rivertale.worldgen.river.RegionClassification;
import org.sosly.rivertale.worldgen.river.EdgeCrossing;
import org.sosly.rivertale.worldgen.river.FlowDirection;
import org.sosly.rivertale.worldgen.river.PathDirection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public class VisualizeD8Packet {

    private final boolean enabled;
    private final List<D8RegionData> regions;

    public VisualizeD8Packet(boolean enabled, List<D8RegionData> regions) {
        this.enabled = enabled;
        this.regions = regions != null ? regions : new ArrayList<>();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
        buf.writeInt(regions.size());

        for (D8RegionData region : regions) {
            buf.writeInt(region.regionX);
            buf.writeInt(region.regionZ);
            buf.writeEnum(region.classification);
            buf.writeBoolean(region.isBasin);

            for (int i = 0; i < 4; i++) {
                EdgeCrossing crossing = region.crossings[i];
                buf.writeBoolean(crossing != null);
                if (crossing != null) {
                    buf.writeInt(crossing.row());
                    buf.writeInt(crossing.col());
                    buf.writeBoolean(crossing.direction() == EdgeCrossing.Direction.OUT);
                }
            }
            buf.writeEnum(region.primaryOutputDirection);

            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    buf.writeEnum(region.flowDirections[row][col]);
                }
            }

            buf.writeInt(region.terminusSubcells.length);
            for (int[] terminus : region.terminusSubcells) {
                buf.writeInt(terminus[0]);
                buf.writeInt(terminus[1]);
            }

            buf.writeInt(region.riverPaths.size());
            for (Map.Entry<PathDirection, List<int[]>> entry : region.riverPaths.entrySet()) {
                buf.writeEnum(entry.getKey());
                buf.writeInt(entry.getValue().size());
                for (int[] subcell : entry.getValue()) {
                    buf.writeInt(subcell[0]);
                    buf.writeInt(subcell[1]);
                }
            }

            buf.writeInt(region.confluenceSubcells.size());
            for (int[] confluence : region.confluenceSubcells) {
                buf.writeInt(confluence[0]);
                buf.writeInt(confluence[1]);
            }
        }
    }

    public static VisualizeD8Packet decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int regionCount = buf.readInt();
        List<D8RegionData> regions = new ArrayList<>(regionCount);

        for (int i = 0; i < regionCount; i++) {
            int regionX = buf.readInt();
            int regionZ = buf.readInt();
            RegionClassification classification = buf.readEnum(RegionClassification.class);
            boolean isBasin = buf.readBoolean();

            EdgeCrossing[] crossings = new EdgeCrossing[4];
            for (int j = 0; j < 4; j++) {
                boolean hasCrossing = buf.readBoolean();
                if (hasCrossing) {
                    int row = buf.readInt();
                    int col = buf.readInt();
                    boolean isOutput = buf.readBoolean();
                    EdgeCrossing.Direction dir = isOutput
                        ? EdgeCrossing.Direction.OUT
                        : EdgeCrossing.Direction.IN;
                    crossings[j] = new EdgeCrossing(row, col, dir);
                }
            }
            PathDirection primaryOutputDirection = buf.readEnum(PathDirection.class);

            FlowDirection[][] flowDirections = new FlowDirection[8][8];
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    flowDirections[row][col] = buf.readEnum(FlowDirection.class);
                }
            }

            int terminusCount = buf.readInt();
            int[][] terminusSubcells = new int[terminusCount][2];
            for (int t = 0; t < terminusCount; t++) {
                terminusSubcells[t][0] = buf.readInt();
                terminusSubcells[t][1] = buf.readInt();
            }

            int pathCount = buf.readInt();
            Map<PathDirection, List<int[]>> riverPaths = new HashMap<>();
            for (int p = 0; p < pathCount; p++) {
                PathDirection dir = buf.readEnum(PathDirection.class);
                int pathLength = buf.readInt();
                List<int[]> path = new ArrayList<>(pathLength);
                for (int s = 0; s < pathLength; s++) {
                    path.add(new int[]{buf.readInt(), buf.readInt()});
                }
                riverPaths.put(dir, path);
            }

            int confluenceCount = buf.readInt();
            List<int[]> confluenceSubcells = new ArrayList<>(confluenceCount);
            for (int c = 0; c < confluenceCount; c++) {
                confluenceSubcells.add(new int[]{buf.readInt(), buf.readInt()});
            }

            regions.add(new D8RegionData(regionX, regionZ, classification, isBasin,
                crossings, primaryOutputDirection, flowDirections,
                terminusSubcells, riverPaths, confluenceSubcells));
        }

        return new VisualizeD8Packet(enabled, regions);
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
        ClientD8Cache.setEnabled(enabled);
        if (enabled) {
            ClientD8Cache.updateRegions(regions);
        }
    }

    public static class D8RegionData {
        public final int regionX;
        public final int regionZ;
        public final RegionClassification classification;
        public final boolean isBasin;
        public final EdgeCrossing[] crossings;
        public final PathDirection primaryOutputDirection;
        public final FlowDirection[][] flowDirections;
        public final int[][] terminusSubcells;
        public final Map<PathDirection, List<int[]>> riverPaths;
        public final List<int[]> confluenceSubcells;

        public D8RegionData(int regionX, int regionZ, RegionClassification classification,
                          boolean isBasin, EdgeCrossing[] crossings,
                          PathDirection primaryOutputDirection, FlowDirection[][] flowDirections,
                          int[][] terminusSubcells, Map<PathDirection, List<int[]>> riverPaths,
                          List<int[]> confluenceSubcells) {
            this.regionX = regionX;
            this.regionZ = regionZ;
            this.classification = classification;
            this.isBasin = isBasin;
            this.crossings = crossings;
            this.primaryOutputDirection = primaryOutputDirection;
            this.flowDirections = flowDirections;
            this.terminusSubcells = terminusSubcells;
            this.riverPaths = riverPaths != null ? riverPaths : new HashMap<>();
            this.confluenceSubcells = confluenceSubcells != null ? confluenceSubcells : new ArrayList<>();
        }
    }
}
