package org.sosly.rivertale.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.command.VisMode;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.RegionType;

@OnlyIn(Dist.CLIENT)
public class ClientRegionCache {

    private static ClientRegionCache instance;
    private static EnumSet<VisMode> enabledModes = EnumSet.noneOf(VisMode.class);

    private final Map<Long, Region> cache;
    private final int capacity;

    public record Cell(CellPos pos, Feature feature, int y, OptionalInt averageY, OptionalDouble flow) {
        public static Cell decode(CompoundTag tag) {
            return new Cell(
                new CellPos(tag.getLong("pos")),
                Feature.valueOf(tag.getString("feature")),
                tag.getInt("y"),
                tag.contains("averageY", Tag.TAG_INT) ? OptionalInt.of(tag.getInt("averageY")) : OptionalInt.empty(),
                tag.contains("flow", Tag.TAG_DOUBLE) ? OptionalDouble.of(tag.getDouble("flow")) : OptionalDouble.empty()
            );
        }
    }

    public record Edge(CellPos from, CellPos to, int fromY, int toY) {
        public static Edge decode(CompoundTag tag) {
            return new Edge(
                new CellPos(tag.getLong("from")),
                new CellPos(tag.getLong("to")),
                tag.getInt("fromY"),
                tag.getInt("toY")
            );
        }
    }

    public record Point(CellPos pos, int y) {
    }

    public record Flowline(List<Point> points) {
        public Flowline {
            points = List.copyOf(points);
        }

        public static Flowline decode(CompoundTag tag) {
            long[] cells = tag.getLongArray("cells");
            int[] heights = tag.getIntArray("heights");
            if (cells.length != heights.length) {
                throw new IllegalArgumentException("Path coordinates and heights must have the same length");
            }
            List<Point> points = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) {
                points.add(new Point(new CellPos(cells[i]), heights[i]));
            }
            return new Flowline(points);
        }
    }

    public record Region(RegionPos pos, RegionType type, List<Boundary> boundaries, List<Cell> cells,
                         List<Edge> edges, List<Flowline> paths, List<Point> outlets) {
        public static Region decode(CompoundTag tag) {
            RegionPos pos = new RegionPos(tag.getLong("pos"));
            RegionType type = RegionType.valueOf(tag.getString("type"));

            List<Boundary> boundaries = new ArrayList<>();
            ListTag boundaryList = tag.getList("boundaries", Tag.TAG_COMPOUND);
            for (int i = 0; i < boundaryList.size(); i++) {
                boundaries.add(Boundary.decode(boundaryList.getCompound(i)));
            }

            List<Cell> cells = new ArrayList<>();
            ListTag cellList = tag.getList("cells", Tag.TAG_COMPOUND);
            for (int i = 0; i < cellList.size(); i++) {
                cells.add(Cell.decode(cellList.getCompound(i)));
            }

            List<Edge> edges = new ArrayList<>();
            ListTag edgeList = tag.getList("watershed", Tag.TAG_COMPOUND);
            for (int i = 0; i < edgeList.size(); i++) {
                edges.add(Edge.decode(edgeList.getCompound(i)));
            }

            List<Flowline> paths = new ArrayList<>();
            ListTag pathList = tag.getList("paths", Tag.TAG_COMPOUND);
            for (int i = 0; i < pathList.size(); i++) {
                paths.add(Flowline.decode(pathList.getCompound(i)));
            }

            List<Point> outlets = new ArrayList<>();
            ListTag outletList = tag.getList("outlets", Tag.TAG_COMPOUND);
            for (int i = 0; i < outletList.size(); i++) {
                CompoundTag outlet = outletList.getCompound(i);
                outlets.add(new Point(new CellPos(outlet.getLong("pos")), outlet.getInt("y")));
            }
            return new Region(pos, type, boundaries, cells, edges, paths, outlets);
        }
    }

    private ClientRegionCache(int capacity) {
        this.capacity = capacity;
        this.cache = new LinkedHashMap<>(capacity, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Long, Region> eldest) {
                return size() > ClientRegionCache.this.capacity;
            }
        };
    }

    public static ClientRegionCache get() {
        if (instance == null) {
            throw new IllegalStateException("ClientRegionCache not initialized");
        }
        return instance;
    }

    public static void init() {
        instance = new ClientRegionCache(CommonConfig.get().caches().clientRegions());
    }

    public static void shutdown() {
        if (instance != null) {
            instance.clear();
            instance = null;
        }
        enabledModes = EnumSet.noneOf(VisMode.class);
    }

    public static boolean isEnabled() {
        return !enabledModes.isEmpty() && instance != null;
    }

    public static boolean isModeEnabled(VisMode mode) {
        return enabledModes.contains(mode);
    }

    public static void setEnabledModes(Set<VisMode> modes) {
        enabledModes = modes.isEmpty()
            ? EnumSet.noneOf(VisMode.class)
            : EnumSet.copyOf(modes);
        if (enabledModes.isEmpty() && instance != null) {
            instance.clear();
        }
    }

    public void put(Region region) {
        cache.put(region.pos().toLong(), region);
    }

    public Region get(RegionPos pos) {
        return cache.get(pos.toLong());
    }

    public Collection<Region> getRegions() {
        return cache.values();
    }

    public void clear() {
        cache.clear();
    }
}
