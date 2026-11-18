package org.sosly.rivertale.command;

import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.minecraft.SharedConstants;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.metric.Distribution;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.river.FlowlineTracer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class MetricsCommandTest {
    private CommonConfig originalConfig;
    private Locale originalLocale;

    @BeforeEach
    void setUp() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        originalConfig = CommonConfig.get();
        originalLocale = Locale.getDefault();
        Locale.setDefault(Locale.ROOT);
        CommonConfig.set(new CommonConfig(8, 1, 1, 0, 1, 3, 288, true, true, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT, 5000, false));
        Store.clear();
    }

    @AfterEach
    void tearDown() {
        Store.clear();
        CommonConfig.set(originalConfig);
        Locale.setDefault(originalLocale);
    }

    @Test
    void reportsPathStatisticsThroughTheCommandAndClearsThemWithOtherMetrics() throws Exception {
        Distribution lengths = Store.getDistribution(FlowlineTracer.class, "lengthBlocks");
        lengths.record(50);
        lengths.record(10);
        lengths.record(100);
        lengths.record(20);
        Distribution totals = Store.getDistribution(FlowlineTracer.class, "nudgingRiseBlocks");
        totals.record(0);
        totals.record(5);
        totals.record(15);
        Distribution steps = Store.getDistribution(FlowlineTracer.class, "uphillNudgeBlocks");
        steps.record(5);
        steps.record(15);
        Distribution falls = Store.getDistribution(FlowlineTracer.class, "netFallBlocks");
        falls.record(37);
        falls.record(20);
        Distribution sampled = Store.getDistribution(FlowlineTracer.class, "cellFallBlocks");
        sampled.record(1);
        sampled.record(0);
        sampled.record(7);
        Distribution flat = Store.getDistribution(FlowlineTracer.class, "carveFlatFallBlocks");
        flat.record(1);
        flat.record(0);
        flat.record(2);
        Distribution descending = Store.getDistribution(FlowlineTracer.class, "carveDescendingFallBlocks");
        descending.record(1);
        descending.record(1);
        descending.record(1);
        Store.getRatio(FlowlineTracer.class, "carveFlatFeasible").success();
        Store.getRatio(FlowlineTracer.class, "carveFlatFeasible").success();
        Store.getRatio(FlowlineTracer.class, "carveDescendingFeasible").success();
        Store.getRatio(FlowlineTracer.class, "carveDescendingFeasible").failure();
        List<String> messages = new ArrayList<>();
        CommandSourceStack source = mock(CommandSourceStack.class);
        doAnswer(invocation -> {
            Supplier<Component> message = invocation.getArgument(0);
            messages.add(message.get().getString());
            return null;
        }).when(source).sendSuccess(any(), eq(false));
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(MetricsCommand.register());

        dispatcher.execute("metrics", source);

        assertEquals("=== RiverTale Metrics ===", messages.get(0));
        assertEquals(10, messages.size());
        assertTrue(messages.contains("FlowlineTracer_lengthBlocks: 4 samples | avg=45.0 min=10.0 max=100.0 med=35.0"));
        assertTrue(messages.contains("FlowlineTracer_nudgingRiseBlocks: 3 samples | avg=6.7 min=0.0 max=15.0 med=5.0"));
        assertTrue(messages.contains("FlowlineTracer_uphillNudgeBlocks: 2 samples | avg=10.0 min=5.0 max=15.0 med=10.0"));
        assertTrue(messages.contains("FlowlineTracer_netFallBlocks: 2 samples | avg=28.5 min=20.0 max=37.0 med=28.5"));
        assertTrue(messages.contains("FlowlineTracer_cellFallBlocks: 3 samples | avg=2.7 min=0.0 max=7.0 med=1.0"));
        assertTrue(messages.contains("FlowlineTracer_carveFlatFallBlocks: 3 samples | avg=1.0 min=0.0 max=2.0 med=1.0"));
        assertTrue(messages.contains("FlowlineTracer_carveDescendingFallBlocks: 3 samples | avg=1.0 min=1.0 max=1.0 med=1.0"));
        assertTrue(messages.contains("FlowlineTracer_carveFlatFeasible: 2/2 (100.0%)"));
        assertTrue(messages.contains("FlowlineTracer_carveDescendingFeasible: 1/2 (50.0%)"));
        Store.clear();
        messages.clear();
        dispatcher.execute("metrics", source);
        assertEquals(List.of("No metrics recorded yet."), messages);
    }

    @Test
    void disabledMetricsDoNotStoreLengthSamples() {
        CommonConfig.set(new CommonConfig(8, 1, 1, 0, 1, 3, 288, false, true, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT, 5000, false));
        Distribution lengths = Store.getDistribution(FlowlineTracer.class, "lengthBlocks");
        lengths.record(1234);

        assertEquals(0, lengths.snapshot().count());
        assertTrue(Store.distributions().isEmpty());
    }
}
