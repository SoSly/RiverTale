package org.sosly.rivertale.river;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import org.mockito.MockedStatic;
import org.mockito.invocation.InvocationOnMock;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionExplorer;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.world.RiverShaping;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Fakes the providers beneath {@link RiverPreparation#prepare(Set)} so tests build prepared
 * river data through the production entry point. Regions come from {@link RegionCache}, working sets from
 * {@link RegionExplorer#discover(BlockPos)} and cells from {@link RiverShaping#preclassify(CellPos)}. Other
 * {@link RiverShaping} calls run for real unless a sea level is set. Source cells keep their flow and height but
 * are classified as {@link Feature#SNOWMELT}. Unless a test supplies its own loader, every cell is plain land from
 * {@link #land(CellPos, int, Feature, List)} at {@link #LAND_HEIGHT} with no flow.
 */
final class RiverWorld implements AutoCloseable {
    /**
     * Default terrain height for land cells whose height a test doesn't care about. It sits well above the default
     * sea level of 63, so a default cell is never a basin, and it leaves room for tests to place cells above or below.
     */
    static final int LAND_HEIGHT = 100;

    private final Map<RegionPos, Region> regions = new LinkedHashMap<>();
    private final Set<CellPos> sources = new HashSet<>();
    private final MockedStatic<RegionCache> cache;
    private final MockedStatic<RegionExplorer> explorer;
    private final MockedStatic<RiverShaping> shaping;
    private Function<RegionPos, Collection<Region>> discovered = pos -> regions.values();
    private Function<CellPos, Cell> cells = pos -> land(pos, LAND_HEIGHT, Feature.NONE, List.of());
    private Integer seaLevel;

    RiverWorld() {
        RegionCache regionCache = mock(RegionCache.class);
        when(regionCache.getOrCompute(any(RegionPos.class)))
            .thenAnswer(call -> regions.getOrDefault(call.getArgument(0), new Region(call.getArgument(0), RegionType.OCEANIC)));
        cache = mockStatic(RegionCache.class);
        cache.when(RegionCache::get).thenReturn(regionCache);
        explorer = mockStatic(RegionExplorer.class);
        explorer.when(() -> RegionExplorer.discover(any(BlockPos.class)))
            .thenAnswer(call -> new LinkedHashSet<>(discovered.apply(new RegionPos((BlockPos) call.getArgument(0)))));
        shaping = mockStatic(RiverShaping.class, this::shape);
    }

    /**
     * Builds a coherent land cell: not ocean, not a basin, at an explicit height with explicit flow and feature.
     */
    static Cell land(CellPos pos, int height, Feature feature, List<FlowDirection> directions) {
        Cell cell = spy(new Cell(pos, Set.of(), List.copyOf(directions), feature));
        doReturn(false).when(cell).isOcean();
        doReturn(false).when(cell).isBasin();
        doReturn(height).when(cell).averageEstimatedTerrainHeight();
        return cell;
    }

    static void configureRouting(int fallback, int away) {
        CommonConfig c = CommonConfig.get();
        CommonConfig.set(new CommonConfig(c.regionSize(), c.cellSize(), c.samplesPerCell(), c.enableMetrics(),
            c.enableNudging(), c.nudgingLimit(), fallback, away, c.riseRunRatio(), c.sourceFlow(), c.flowPerCell(),
            c.sources(), c.caches(), c.disableChunkSaving(), c.carvingSearchRadius(), c.maxLakeCells(), c.maxBacktrackCells(), c.maxBacktrackRise()));
    }

    RiverWorld regions(Collection<Region> values) {
        regions.clear();
        values.forEach(region -> regions.put(region.pos(), region));
        return this;
    }

    RiverWorld discover(Function<RegionPos, Collection<Region>> provider) {
        discovered = provider;
        return this;
    }

    RiverWorld cells(Function<CellPos, Cell> loader) {
        cells = loader;
        return this;
    }

    void seaLevel(int level) {
        seaLevel = level;
    }

    FlowlineTracer prepare(RegionPos center, CellPos... traced) {
        sources.clear();
        sources.addAll(List.of(traced));
        Set<RegionPos> visible = new LinkedHashSet<>(regions.keySet());
        visible.add(center);
        sources.forEach(pos -> visible.add(pos.getRegion()));
        return new FlowlineTracer(RiverPreparation.prepare(visible));
    }

    @Override
    public void close() {
        shaping.close();
        explorer.close();
        cache.close();
    }

    private Object shape(InvocationOnMock call) throws Throwable {
        String method = call.getMethod().getName();
        if (method.equals("preclassify")) {
            return classify(call.getArgument(0));
        }
        if (method.equals("getSeaLevel") && seaLevel != null) {
            return seaLevel;
        }
        return call.callRealMethod();
    }

    private Cell classify(CellPos pos) {
        Cell cell = cells.apply(pos);
        if (!sources.contains(pos)) {
            return cell;
        }
        return land(pos, cell.averageEstimatedTerrainHeight(), Feature.SNOWMELT, cell.flowDirections());
    }
}
