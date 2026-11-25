package org.sosly.rivertale.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Distribution;
import org.sosly.rivertale.metric.Ratio;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.RegionTypeCache;
import org.sosly.rivertale.river.FlowlineTracer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MetricsCommandTest {
    private static final String FOOTER = "More: /rivertale metrics <pipeline|rivers|cache>";

    private final List<String> messages = new ArrayList<>();
    private final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    private CommandSourceStack source;
    private CommonConfig originalConfig;

    @BeforeEach
    void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalConfig = CommonConfig.get();
        CommonConfig.set(new CommonConfig(8, 1, 1, true, true, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        Store.clear();

        source = mock(CommandSourceStack.class);
        when(source.hasPermission(2)).thenReturn(true);
        doAnswer(invocation -> {
            Supplier<Component> message = invocation.getArgument(0);
            messages.add(message.get().getString());
            return null;
        }).when(source).sendSuccess(any(), eq(false));
        dispatcher.register(Commands.literal("rivertale").then(MetricsCommand.register()));
    }

    @AfterEach
    void tearDown() {
        Store.clear();
        CommonConfig.set(originalConfig);
    }

    @Test
    void requiresOperatorPermissionForTheOverviewAndEachTopic() {
        when(source.hasPermission(2)).thenReturn(false);

        for (String input : List.of("", " pipeline", " rivers", " cache")) {
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("rivertale metrics" + input, source));
        }
    }

    @Test
    void offersExactlyTheThreeTopicsForCompletion() throws Exception {
        var parsed = dispatcher.parse("rivertale metrics ", source);

        var suggestions = dispatcher.getCompletionSuggestions(parsed).get().getList();

        assertEquals(List.of("cache", "pipeline", "rivers"), suggestions.stream().map(Suggestion::getText).toList());
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("rivertale metrics all", source));
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("rivertale metrics detail", source));
    }

    @Test
    void overviewShowsStageTimingsRiverRecordsAndCacheRatesOnly() throws Exception {
        Timer network = Store.getTimer(riverType("RiverNetwork"), "build");
        network.record(280_133_500);
        network.record(4_646_109_900L);
        Store.getTimer(riverType("RiverPreparation"), "prepare").record(1_224_410_100);
        Store.getTimer(riverType("FlowlinePreparation"), "prepare").record(182_274_300);
        Store.getTimer(riverType("WatershedPreparation"), "gather").record(62_232_100);
        Store.getTimer(riverType("WatershedBuilder"), "build").record(9_774_900);
        Store.getTimer(riverType("RiverGraph"), "routing").record(509_649_800);
        Store.getDistribution(FlowlineTracer.class, "lengthBlocks").record(64);
        Store.getDistribution(FlowlineTracer.class, "lengthBlocks").record(5215.8);
        Store.getDistribution(FlowlineTracer.class, "netFallBlocks").record(297);
        Store.getDistribution(riverType("WatershedBuilder"), "riverCells").record(167);
        Store.getRatio(FlowlineTracer.class, "success").success();
        seedCaches();

        assertEquals(1, dispatcher.execute("rivertale metrics", source));

        assertEquals(List.of(
            "RiverTale Metrics · 2 network builds",
            "Pipeline (ms) Avg Min Max",
            "Whole network build 2463.1 280.1 4646.1",
            "Prepare river data 1224.4 1224.4 1224.4",
            "Prepare flowlines 182.3 182.3 182.3",
            "Gather watershed inputs 62.2 62.2 62.2",
            "Build watersheds 9.8 9.8 9.8",
            "River records",
            "Longest traced path: 5,215.8 blocks",
            "Greatest source-to-sea drop: 297 blocks",
            "Largest watershed: 167 river cells",
            "Cache hit rates",
            "Region type 0.0% · Samples 66.7%",
            FOOTER
        ), messages.stream().map(line -> line.strip().replaceAll(" +", " ")).toList());
    }

    @Test
    void pipelineShowsSubstageTimingAndWorkloadWithoutRiverOrCacheData() throws Exception {
        Timer routing = Store.getTimer(riverType("RiverGraph"), "routing");
        routing.record(4_000);
        routing.record(509_649_800);
        Distribution regions = Store.getDistribution(riverType("RiverPreparation"), "regions");
        regions.record(10);
        regions.record(32);
        Store.getDistribution(FlowlineTracer.class, "lengthBlocks").record(5215.8);
        seedCaches();

        dispatcher.execute("rivertale metrics pipeline", source);

        assertEquals(List.of(
            "RiverTale Metrics · Pipeline diagnostics",
            "Prepare river data",
            "  River routing · 2 calls",
            "    Avg 254.83 ms · Min 4.0 µs · Max 509.65 ms · Median 4.0 µs",
            "  Regions · 2 samples",
            "    Avg 21.0 · Min 10.0 · Max 32.0 · Median 21.0"
        ), messages);
    }

    @Test
    void riversShowsTraceOutcomesAndDistributionsWithoutTimingOrCacheData() throws Exception {
        Store.getTimer(FlowlineTracer.class, "trace").record(4_000);
        Store.getRatio(FlowlineTracer.class, "success").success();
        Store.getRatio(FlowlineTracer.class, "success").failure();
        Store.getRatio(FlowlineTracer.class, "carveFlatFeasible").success();
        Store.getRatio(FlowlineTracer.class, "carveDescendingFeasible").failure();
        Store.getDistribution(FlowlineTracer.class, "awaySteps").record(4);
        Store.getDistribution(riverType("WatershedBuilder"), "riverCells").record(167);
        seedCaches();

        dispatcher.execute("rivertale metrics rivers", source);

        assertEquals(List.of(
            "RiverTale Metrics · River diagnostics",
            "Flowlines",
            "  Valid traces: 1 / 2 (50.0%)",
            "  Flat carve feasible: 1 / 1 (100.0%)",
            "  Descending carve feasible: 0 / 1 (0.0%)",
            "  Away steps · 1 sample",
            "    Avg 4.0 · Min 4.0 · Max 4.0 · Median 4.0",
            "Watersheds",
            "  River cells · 1 sample",
            "    Avg 167.0 · Min 167.0 · Max 167.0 · Median 167.0"
        ), messages);
    }

    @Test
    void cacheShowsHitsMissesAndRatesInAscendingHitRateOrder() throws Exception {
        seedCaches();
        Store.getRatio(FlowlineTracer.class, "success").success();
        Store.getTimer(FlowlineTracer.class, "trace").record(4_000);

        dispatcher.execute("rivertale metrics cache", source);

        assertEquals(List.of(
            "RiverTale Metrics · Cache hit rates",
            "  Region type: 0 / 2 hits (0.0%) · 2 missed",
            "  Samples: 2 / 3 hits (66.7%) · 1 missed"
        ), messages);
    }

    @Test
    void emptyOverviewStillListsTopicsAndAnEmptyTopicDoesNotShowUnrelatedData() throws Exception {
        dispatcher.execute("rivertale metrics", source);
        assertEquals(List.of("No metrics recorded yet.", FOOTER), messages);
        messages.clear();
        seedCaches();

        dispatcher.execute("rivertale metrics rivers", source);

        assertEquals(List.of("RiverTale Metrics · River diagnostics", "No metrics recorded yet."), messages);
    }

    @Test
    void overviewStillShowsWholeNetworkBuildWhenMetricsAreDisabled() throws Exception {
        CommonConfig.set(new CommonConfig(8, 1, 1, false, true, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        Store.getAlwaysOnTimer(riverType("RiverNetwork"), "build").record(14_000_000_000L);
        Store.getTimer(riverType("RiverPreparation"), "prepare").record(9_000_000_000L);

        dispatcher.execute("rivertale metrics", source);

        assertEquals(List.of(
            "RiverTale Metrics · 1 network build",
            "Pipeline (ms) Avg Min Max",
            "Whole network build 14000.0 14000.0 14000.0",
            FOOTER
        ), messages.stream().map(line -> line.strip().replaceAll(" +", " ")).toList());
    }

    @Test
    void disabledMetricsDoNotStoreLengthSamples() {
        CommonConfig.set(new CommonConfig(8, 1, 1, false, true, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        Distribution lengths = Store.getDistribution(FlowlineTracer.class, "lengthBlocks");
        lengths.record(1234);

        assertEquals(0, lengths.snapshot().count());
        assertTrue(Store.distributions().isEmpty());
    }

    private static Class<?> riverType(String name) throws ClassNotFoundException {
        return Class.forName(FlowlineTracer.class.getPackageName() + "." + name);
    }

    private static void seedCaches() {
        Ratio samples = Store.getRatio(SampleCache.class, "hits");
        samples.success();
        samples.success();
        samples.failure();
        Ratio regionType = Store.getRatio(RegionTypeCache.class, "hits");
        regionType.failure();
        regionType.failure();
    }
}
