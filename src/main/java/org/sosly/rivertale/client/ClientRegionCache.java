package org.sosly.rivertale.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.Cache;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.terrain.OceanBoundary;

@OnlyIn(Dist.CLIENT)
public class ClientRegionCache implements Cache<ClientRegionCache.Region> {
    private static final int DEFAULT_CAPACITY = 100;

    private static ClientRegionCache instance;
    private static boolean enabled = false;

    private final Map<Long, Region> cache;
    private final int capacity;

    public record Cell(CellPos pos, Feature feature, Direction flow) {
        public static Cell decode(CompoundTag tag) {
            return new Cell(
                new CellPos(tag.getLong("pos")),
                Feature.valueOf(tag.getString("feature")),
                Direction.valueOf(tag.getString("flow"))
            );
        }
    }

    public record Edge(CellPos from, CellPos to) {
        public static Edge decode(CompoundTag tag) {
            return new Edge(
                new CellPos(tag.getLong("from")),
                new CellPos(tag.getLong("to"))
            );
        }
    }

    public record Region(RegionPos pos, RegionType type, Set<OceanBoundary> boundaries, List<Cell> cells,
                         List<Edge> edges) {
        public static Region decode(CompoundTag tag) {
            RegionPos pos = new RegionPos(tag.getLong("pos"));
            RegionType type = RegionType.valueOf(tag.getString("type"));

            Set<OceanBoundary> boundaries = new HashSet<>();
            ListTag boundaryList = tag.getList("boundaries", Tag.TAG_COMPOUND);
            for (int i = 0; i < boundaryList.size(); i++) {
                boundaries.add(OceanBoundary.decode(boundaryList.getCompound(i)));
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

            return new Region(pos, type, boundaries, cells, edges);
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
        instance = new ClientRegionCache(DEFAULT_CAPACITY);
    }

    public static void shutdown() {
        if (instance != null) {
            instance.clear();
            instance = null;
        }
        enabled = false;
    }

    public static boolean isEnabled() {
        return enabled && instance != null;
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!enabled && instance != null) {
            instance.clear();
        }
    }

    public void put(Region region) {
        cache.put(region.pos().toLong(), region);
    }

    @Override
    public Region getOrCompute(int x, int z) {
        return cache.get(RegionPos.asLong(x, z));
    }

    public Region get(RegionPos pos) {
        return cache.get(pos.toLong());
    }

    public Collection<Region> getRegions() {
        return cache.values();
    }

    @Override
    public void clear() {
        cache.clear();
    }
}
