package org.sosly.rivertale.region;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.client.ClientRegionCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.networking.Message;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.river.Path;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.river.WatershedCache;
import org.sosly.rivertale.terrain.OceanBoundary;
import org.sosly.rivertale.terrain.Oceans;

public class Region {

    private final Set<OceanBoundary> boundaries = new HashSet<>();
    private final RegionPos pos;
    private final RegionType type;

    Region(RegionPos pos, RegionType type) {
        this.pos = pos;
        this.type = type;
    }

    static Region createSkeleton(RegionPos pos, CellCache cellCache, SampleCache sampleCache) {
        RegionType type = RegionTypeCache.get().getOrCompute(pos, sampleCache);
        Region region = new Region(pos, type);

        if (type == RegionType.COASTAL) {
            for (OceanBoundary boundary : Oceans.boundaries(pos, sampleCache)) {
                region.addBoundary(boundary);
            }
        }

        return region;
    }

    void computePaths(CellCache cellCache, SampleCache sampleCache) {
        if (type == RegionType.COASTAL || type == RegionType.FLUVIAL) {
            tracePaths(cellCache, sampleCache);
        }
    }

    private void tracePaths(CellCache cellCache, SampleCache sampleCache) {
        Set<Region> traceRegions = buildTraceRegions(cellCache, sampleCache);
        if (traceRegions.isEmpty()) {
            return;
        }

        WatershedCache watershedCache = WatershedCache.get();
        for (CellPos cellPos : cells()) {
            Cell cell = cellCache.getOrCompute(cellPos);
            if (cell.feature().type != CellType.SOURCE) {
                continue;
            }

            Path path = new Path(cellPos, traceRegions);
            path.trace();
            if (path.isValid()) {
                watershedCache.addPath(path);
            }
        }
    }

    private Set<Region> buildTraceRegions(CellCache cellCache, SampleCache sampleCache) {
        Set<Region> regions = new HashSet<>();
        Set<RegionPos> visited = new HashSet<>();

        addRegionChain(pos, type, regions, visited, cellCache, sampleCache);

        return regions;
    }

    private void addRegionChain(RegionPos regionPos, RegionType regionType,
                                Set<Region> regions, Set<RegionPos> visited,
                                CellCache cellCache, SampleCache sampleCache) {
        if (visited.contains(regionPos)) {
            return;
        }
        visited.add(regionPos);

        Region region = RegionCache.get().getOrCompute(regionPos, cellCache, sampleCache);
        regions.add(region);

        if (regionType == RegionType.FLUVIAL) {
            for (FlowDirection dir : FlowDirection.D8) {
                RegionPos neighborPos = regionPos.relative(dir);
                RegionType neighborType = RegionTypeCache.get().getOrCompute(neighborPos, sampleCache);
                if (neighborType == RegionType.COASTAL) {
                    addRegionChain(neighborPos, neighborType, regions, visited, cellCache, sampleCache);
                }
            }
        }
    }

    void addBoundary(OceanBoundary boundary) {
        boundaries.add(boundary);
    }

    public Set<OceanBoundary> boundaries() {
        return Collections.unmodifiableSet(boundaries);
    }

    List<CellPos> cells() {
        List<CellPos> result = new ArrayList<>();
        CellPos min = pos.getMinCell();
        int count = CommonConfig.get().cellsPerRegion();
        for (int x = 0; x < count; x++) {
            for (int z = 0; z < count; z++) {
                result.add(new CellPos(min.x() + x, min.z() + z));
            }
        }
        return result;
    }

    public RegionPos pos() {
        return pos;
    }

    public RegionType type() {
        return type;
    }

    public CompoundTag encode() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("pos", pos.toLong());
        tag.putString("type", type.name());

        ListTag boundaryList = new ListTag();
        for (OceanBoundary boundary : boundaries) {
            boundaryList.add(boundary.encode());
        }
        tag.put("boundaries", boundaryList);

        CellCache cellCache = CellCache.get();
        ListTag cellList = new ListTag();
        for (CellPos cellPos : cells()) {
            Cell cell = cellCache.getOrCompute(cellPos);
            CompoundTag cellTag = new CompoundTag();
            cellTag.putLong("pos", cellPos.toLong());
            cellTag.putString("feature", cell.feature().name());
            cellTag.putString("flow", cell.flowDirections().isEmpty()
                ? FlowDirection.NONE.name()
                : cell.flowDirections().get(0).name());
            int y = cell.entryY() != null ? cell.entryY() : cell.averageEstimatedTerrainHeight();
            cellTag.putInt("y", y);
            cellList.add(cellTag);
        }
        tag.put("cells", cellList);

        ListTag watershedList = new ListTag();
        WatershedCache watershedCache = WatershedCache.get();
        if (watershedCache != null) {
            Set<Watershed> seen = new HashSet<>();
            for (CellPos cellPos : cells()) {
                Watershed watershed = watershedCache.getWatershed(cellPos);
                if (watershed != null && seen.add(watershed)) {
                    ListTag edges = watershed.encode(cellCache);
                    for (int i = 0; i < edges.size(); i++) {
                        watershedList.add(edges.getCompound(i));
                    }
                }
            }
        }
        tag.put("watershed", watershedList);

        return tag;
    }

    public static class Packet extends Message {
        private CompoundTag data;

        public Packet(Region region) {
            this.data = region.encode();
        }

        private Packet(CompoundTag data) {
            this.data = data;
        }

        public static Packet decode(FriendlyByteBuf buf) {
            CompoundTag tag = buf.readNbt();
            if (tag == null) {
                LogUtils.getLogger().warn("Received null NBT in Region packet");
                return new Packet(new CompoundTag());
            }
            return new Packet(tag);
        }

        public static void encode(Packet msg, FriendlyByteBuf buf) {
            buf.writeNbt(msg.data);
        }

        public static void handle(Packet msg, Supplier<NetworkEvent.Context> ctx) {
            NetworkEvent.Context context = ctx.get();
            if (context.getDirection() == NetworkDirection.PLAY_TO_CLIENT) {
                context.enqueueWork(() -> handleClient(msg.data));
            }
            context.setPacketHandled(true);
        }

        private static void handleClient(CompoundTag data) {
            if (data.isEmpty()) {
                return;
            }
            ClientRegionCache.Region region = ClientRegionCache.Region.decode(data);
            ClientRegionCache.get().put(region);
        }
    }
}
