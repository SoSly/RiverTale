package org.sosly.rivertale.river;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Boundary;
import org.sosly.rivertale.core.BoundaryType;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionExplorer;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.world.RiverShaping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class RoutingConfigTest {
    private CommonConfig original;
    private MockedStatic<RegionExplorer> explorer;
    private MockedStatic<RiverShaping> shaping;

    @BeforeEach
    void setUp() {
        original = CommonConfig.get();
        explorer = mockStatic(RegionExplorer.class);
        shaping = mockStatic(RiverShaping.class);
    }

    @AfterEach
    void tearDown() {
        shaping.close();
        explorer.close();
        CommonConfig.Spec.SPEC.setConfig((CommentedConfig) null);
        CommonConfig.set(original);
    }

    @Test
    void configuredAllowanceControlsProductionTracingAndReverseDiscovery() {
        CellPos source = new CellPos(3, 3);
        CellPos away = new CellPos(2, 3);
        CellPos turn = new CellPos(3, 2);
        CellPos outlet = new CellPos(4, 3);
        terrain(outlet, Map.of(source, cell(FlowDirection.WEST), away, cell(FlowDirection.NORTHEAST),
            turn, cell(FlowDirection.SOUTHEAST)));

        configure(0, 0);
        FlowlineTracer strict = preparedTracer(source);
        assertFalse(strict.trace(source).isValid());
        UpstreamSearch strictUpstream = new UpstreamSearch(strict);
        strictUpstream.expand(List.of(outlet));
        assertEquals(3, strictUpstream.cellCount());

        configure(0, 1);
        FlowlineTracer detours = preparedTracer(source);
        assertEquals(List.of(source, away, turn, outlet), detours.trace(source).cells());
        assertTrue(detours.trace(source).isValid());
        UpstreamSearch detourUpstream = new UpstreamSearch(detours);
        detourUpstream.expand(List.of(outlet));
        assertEquals(4, detourUpstream.cellCount());
        assertFalse(strict.trace(source).isValid());
    }

    @Test
    void configuredFallbackRadiusCanBeReducedOrDisabled() {
        CellPos source = new CellPos(1, 2);
        CellPos outlet = new CellPos(3, 2);
        terrain(outlet, Map.of());
        configure(0, 0);
        assertFalse(preparedTracer(source).trace(source).isValid());
        configure(1, 0);
        assertFalse(preparedTracer(source).trace(source).isValid());
        configure(2, 0);
        Flowline path = preparedTracer(source).trace(source);
        assertTrue(path.isValid());
        assertEquals(List.of(source, new CellPos(2, 2), outlet), path.cells());
    }

    private FlowlineTracer preparedTracer(CellPos source) {
        Set<Region> regions = RegionExplorer.discover(source.getRegion().getWorldPosition(0));
        return new FlowlineTracer(regions, RiverShaping::classify, CommonConfig.get().fallbackApproachCells());
    }

    private void configure(int fallback, int away) {
        CommentedConfig data = new TomlParser().parse("""
            regionSize = 8
            cellSize = 1
            enableNudging = false
            """);
        data.set("fallbackApproachCells", fallback);
        data.set("awayStepAllowance", away);
        CommonConfig.Spec.SPEC.setConfig(data);
        CommonConfig.Spec.load();
    }

    private void terrain(CellPos outlet, Map<CellPos, Cell> cells) {
        Region region = new Region(outlet.getRegion(), RegionType.COASTAL,
            List.of(new Boundary(outlet, outlet.relative(FlowDirection.EAST), BoundaryType.OCEAN)));
        explorer.when(() -> RegionExplorer.discover(any(BlockPos.class))).thenReturn(Set.of(region));
        Cell empty = cell();
        shaping.when(() -> RiverShaping.classify(any(CellPos.class)))
            .thenAnswer(invocation -> cells.getOrDefault(invocation.getArgument(0), empty));
    }

    private Cell cell(FlowDirection... directions) {
        Cell cell = mock(Cell.class);
        when(cell.feature()).thenReturn(Feature.NONE);
        when(cell.flowDirections()).thenReturn(List.of(directions));
        return cell;
    }
}
