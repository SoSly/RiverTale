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
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.networking.Message;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.river.Path;
import org.sosly.rivertale.terrain.OceanBoundary;
import org.sosly.rivertale.terrain.Oceans;

public class Region {

    private final Set<OceanBoundary> boundaries = new HashSet<>();
    private final List<Path> paths = new ArrayList<>();
    private final RegionPos pos;
    private final RegionType type;

    Region(RegionPos pos, RegionType type) {
        this.pos = pos;
        this.type = type;
    }

    public static Region create(RegionPos pos, CellCache cellCache, SampleCache sampleCache) {
        RegionType type = RegionType.classify(pos, sampleCache);
        Region region = new Region(pos, type);

        if (type == RegionType.COASTAL && sampleCache != null) {
            for (OceanBoundary boundary : Oceans.boundaries(pos, cellCache, sampleCache)) {
                region.addBoundary(boundary);
            }
        }

        if (type == RegionType.COASTAL || type == RegionType.FLUVIAL) {
            region.tracePaths(cellCache, sampleCache);
        }

        return region;
    }

    private void tracePaths(CellCache cellCache, SampleCache sampleCache) {
        Set<Region> traceRegions = buildTraceRegions(cellCache, sampleCache);
        if (traceRegions.isEmpty()) {
            return;
        }

        for (Cell cell : cells()) {
            if (cell.feature().type != CellType.SOURCE) {
                continue;
            }

            Path path = new Path(cell, traceRegions);
            path.trace();
            if (path.isValid()) {
                paths.add(path);
            }
        }
    }

    private Set<Region> buildTraceRegions(CellCache cellCache, SampleCache sampleCache) {
        Set<Region> regions = new HashSet<>();
        regions.add(this);

        if (type == RegionType.FLUVIAL) {
            Set<Direction> neighbors = findCoastalNeighborDirections(sampleCache);
            for (Direction dir : neighbors) {
                RegionPos neighbor = pos.relative(dir);
                regions.add(RegionCache.get().getOrCompute(neighbor, cellCache, sampleCache));
            }
        }

        return regions;
    }

    private Set<Direction> findCoastalNeighborDirections(SampleCache sampleCache) {
        int avgDx = 0;
        int avgDz = 0;
        for (Cell cell : cells()) {
            Direction flow = cell.flowDirection();
            if (flow != Direction.NONE) {
                avgDx += flow.dx;
                avgDz += flow.dz;
            }
        }

        Set<Direction> neighbors = new HashSet<>();

        for (Direction dir : Direction.D8) {
            RegionPos neighborPos = pos.relative(dir);
            RegionType neighborType = RegionType.classify(neighborPos, sampleCache);
            if (neighborType != RegionType.COASTAL) {
                continue;
            }

            neighbors.add(dir);
        }

        return neighbors;
    }

    void addBoundary(OceanBoundary boundary) {
        boundaries.add(boundary);
    }

    public Set<OceanBoundary> boundaries() {
        return Collections.unmodifiableSet(boundaries);
    }

    public List<Path> paths() {
        return Collections.unmodifiableList(paths);
    }

    List<Cell> cells() {
        List<Cell> result = new ArrayList<>();
        CellPos min = pos.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();
        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                result.add(CellCache.get().getOrCompute(new CellPos(min.x() + x, min.z() + z)));
            }
        }
        return result;
    }

    Cell getCell(CellPos cellPos) {
        return CellCache.get().getOrCompute(cellPos);
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

        ListTag cellList = new ListTag();
        for (Cell cell : cells()) {
            CompoundTag cellTag = new CompoundTag();
            cellTag.putLong("pos", cell.pos().toLong());
            cellTag.putString("feature", cell.feature().name());
            cellTag.putString("flow", cell.flowDirection().name());
            cellList.add(cellTag);
        }
        tag.put("cells", cellList);

        ListTag pathList = new ListTag();
        for (Path path : paths) {
            pathList.add(path.encode());
        }
        tag.put("paths", pathList);

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
