package org.sosly.rivertale.network;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.rivertale.cell.features.Feature;
import org.sosly.rivertale.cell.Grid;
import org.sosly.rivertale.client.RegionRenderer;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.poc.vis.Region;
import org.sosly.rivertale.poc.vis.RegionType;
import org.sosly.rivertale.poc.vis.OceanBoundary;

public class RegionPacket {
    private final List<Region> regions;
    private final Map<Long, Grid> flows;
    private final int regionSize;
    private final int cellsPerRegion;

    public RegionPacket(List<Region> regions, Map<Long, Grid> flows, int regionSize, int cellsPerRegion) {
        this.regions = regions;
        this.flows = flows;
        this.regionSize = regionSize;
        this.cellsPerRegion = cellsPerRegion;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(regionSize);
        buf.writeInt(cellsPerRegion);
        buf.writeInt(regions.size());
        for (Region region : regions) {
            buf.writeInt(region.regionX());
            buf.writeInt(region.regionZ());
            buf.writeInt(region.type().ordinal());
            buf.writeInt(region.size());
            for (OceanBoundary b : region.boundaries()) {
                buf.writeInt(b.landX());
                buf.writeInt(b.landZ());
                buf.writeInt(b.oceanX());
                buf.writeInt(b.oceanZ());
            }

            long key = regionKey(region.regionX(), region.regionZ());
            Grid flow = flows.get(key);
            buf.writeBoolean(flow != null);
            if (flow != null) {
                for (int row = 0; row < cellsPerRegion; row++) {
                    for (int col = 0; col < cellsPerRegion; col++) {
                        buf.writeByte(flow.getFlowAt(row, col).ordinal());
                        buf.writeByte(flow.get(row, col).feature().ordinal());
                    }
                }
            }
        }
    }

    public static RegionPacket decode(FriendlyByteBuf buf) {
        int regionSize = buf.readInt();
        int cellsPerRegion = buf.readInt();
        int regionCount = buf.readInt();
        List<Region> regions = new ArrayList<>(regionCount);
        Map<Long, Grid> flows = new HashMap<>();

        for (int r = 0; r < regionCount; r++) {
            int regionX = buf.readInt();
            int regionZ = buf.readInt();
            RegionType type = RegionType.values()[buf.readInt()];
            int boundaryCount = buf.readInt();
            Region region = new Region(regionX, regionZ, type);
            for (int i = 0; i < boundaryCount; i++) {
                int landX = buf.readInt();
                int landZ = buf.readInt();
                int oceanX = buf.readInt();
                int oceanZ = buf.readInt();
                region.put(new OceanBoundary(landX, landZ, oceanX, oceanZ));
            }
            regions.add(region);

            boolean hasFlow = buf.readBoolean();
            if (hasFlow) {
                RegionPos pos = new RegionPos(regionX, regionZ);
                Grid flow = Grid.create(pos, cellsPerRegion);
                for (int row = 0; row < cellsPerRegion; row++) {
                    for (int col = 0; col < cellsPerRegion; col++) {
                        flow.get(row, col).setFlowDirection(Direction.values()[buf.readByte()]);
                        flow.get(row, col).setFeature(Feature.values()[buf.readByte()]);
                    }
                }
                flows.put(regionKey(regionX, regionZ), flow);
            }
        }
        return new RegionPacket(regions, flows, regionSize, cellsPerRegion);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                RegionRenderer.setRegions(regions, flows, regionSize, cellsPerRegion);
            });
        });
        ctx.get().setPacketHandled(true);
    }

    private static long regionKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
