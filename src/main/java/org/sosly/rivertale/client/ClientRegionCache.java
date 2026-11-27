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
    private long meanderSeed;
    private WatershedPreview preview;

    public record Cell(CellPos pos, Feature feature, int y, OptionalInt averageY, OptionalDouble flow, int overFill) {
        public Cell(CellPos pos, Feature feature, int y, OptionalInt averageY, OptionalDouble flow) {
            this(pos, feature, y, averageY, flow, 0);
        }
    }

    public record Edge(CellPos from, CellPos to, int fromY, int toY) {
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

            List<Cell> cells = decodeCells(pos, tag.getCompound("cells"));
            List<Edge> edges = decodeEdges(tag.getCompound("watershed"));

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

        private static List<Cell> decodeCells(RegionPos region, CompoundTag tag) {
            int side = tag.getInt("side");
            byte[] ids = tag.getByteArray("features");
            int[] heights = tag.getIntArray("heights");
            int[] averages = tag.getIntArray("averageYByIndex");
            long[] flows = tag.getLongArray("flowByIndex");
            int[] fills = tag.getIntArray("overFillByIndex");
            if (side < 8 || side > 64 || ids.length != side * side || ids.length != heights.length
                || averages.length % 2 != 0 || flows.length % 2 != 0 || fills.length % 2 != 0) {
                throw new IllegalArgumentException("Invalid preview cell arrays");
            }

            Feature[] features = Feature.values();
            int minX = region.x() * side;
            int minZ = region.z() * side;
            List<Cell> cells = new ArrayList<>(ids.length);
            int averageIndex = 0;
            int flowIndex = 0;
            int fillIndex = 0;
            for (int i = 0; i < ids.length; i++) {
                int featureId = Byte.toUnsignedInt(ids[i]);
                if (featureId >= features.length) {
                    throw new IllegalArgumentException("Invalid preview feature ID: " + featureId);
                }
                CellPos pos = new CellPos(minX + i / side, minZ + i % side);
                OptionalInt average = OptionalInt.empty();
                if (averageIndex < averages.length && averages[averageIndex] == i) {
                    average = OptionalInt.of(averages[averageIndex + 1]);
                    averageIndex += 2;
                }
                OptionalDouble flow = OptionalDouble.empty();
                if (flowIndex < flows.length && flows[flowIndex] == i) {
                    flow = OptionalDouble.of(Double.longBitsToDouble(flows[flowIndex + 1]));
                    flowIndex += 2;
                }
                int depth = 0;
                if (fillIndex < fills.length && fills[fillIndex] == i) {
                    depth = fills[fillIndex + 1];
                    fillIndex += 2;
                }
                cells.add(new Cell(pos, features[featureId], heights[i], average, flow, depth));
            }
            if (averageIndex != averages.length || flowIndex != flows.length || fillIndex != fills.length) {
                throw new IllegalArgumentException("Invalid preview cell indices");
            }
            return cells;
        }

        private static List<Edge> decodeEdges(CompoundTag tag) {
            long[] positions = tag.getLongArray("positions");
            int[] heights = tag.getIntArray("heights");
            if (positions.length != heights.length || positions.length % 2 != 0) {
                throw new IllegalArgumentException("Invalid preview edge arrays");
            }
            List<Edge> edges = new ArrayList<>(positions.length / 2);
            for (int i = 0; i < positions.length; i += 2) {
                edges.add(new Edge(new CellPos(positions[i]), new CellPos(positions[i + 1]), heights[i], heights[i + 1]));
            }
            return edges;
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
        if (!region.equals(cache.put(region.pos().toLong(), region))) {
            preview = null;
        }
    }

    public Region get(RegionPos pos) {
        return cache.get(pos.toLong());
    }

    public Collection<Region> getRegions() {
        return cache.values();
    }

    public void setMeanderSeed(long seed) {
        if (seed != meanderSeed) {
            meanderSeed = seed;
            preview = null;
        }
    }

    WatershedPreview preview() {
        if (preview == null) {
            preview = WatershedPreview.build(cache.values(), meanderSeed);
        }
        return preview;
    }

    public void clear() {
        preview = null;
        cache.clear();
    }
}
