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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Fakes the providers beneath {@link RiverPreparation#prepare(RegionPos, Set, boolean)} so tests build prepared
 * river data through the production entry point. Regions come from {@link RegionCache}, working sets from
 * {@link RegionExplorer#discover(BlockPos)} and cells from {@link RiverShaping#preclassify(CellPos)}. Other
 * {@link RiverShaping} calls run for real unless a sea level is set. Source cells keep their flow and height but
 * are classified as {@link Feature#SNOWMELT}.
 */
final class RiverWorld implements AutoCloseable {
    private final Map<RegionPos, Region> regions = new LinkedHashMap<>();
    private final Set<CellPos> sources = new HashSet<>();
    private final MockedStatic<RegionCache> cache;
    private final MockedStatic<RegionExplorer> explorer;
    private final MockedStatic<RiverShaping> shaping;
    private Function<RegionPos, Collection<Region>> discovered = pos -> regions.values();
    private Function<CellPos, Cell> cells = pos -> new Cell(pos, Set.of());
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

    static void configureRouting(int fallback, int away) {
        CommonConfig c = CommonConfig.get();
        CommonConfig.set(new CommonConfig(c.regionSize(), c.cellSize(), c.samplesPerCell(), c.enableMetrics(),
            c.enableNudging(), c.nudgingLimit(), fallback, away, c.sources(), c.caches(), c.localRiverPreview()));
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
        return new FlowlineTracer(RiverPreparation.prepare(center, visible, false));
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
        List<FlowDirection> directions = cell.flowDirections();
        int height = cell.averageEstimatedTerrainHeight();
        Cell source = mock(Cell.class);
        when(source.feature()).thenReturn(Feature.SNOWMELT);
        when(source.flowDirections()).thenReturn(directions);
        when(source.averageEstimatedTerrainHeight()).thenReturn(height);
        return source;
    }
}
