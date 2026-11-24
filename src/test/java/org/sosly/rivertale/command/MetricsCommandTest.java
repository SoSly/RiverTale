package org.sosly.rivertale.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
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
import org.sosly.rivertale.config.CacheConfig;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.config.SourceConfig;
import org.sosly.rivertale.metric.Distribution;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
        CommonConfig.set(new CommonConfig(8, 1, 1, true, true, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        Store.clear();
    }

    @AfterEach
    void tearDown() {
        Store.clear();
        CommonConfig.set(originalConfig);
        Locale.setDefault(originalLocale);
    }

    @Test
    void rejectsSourcesBelowOperatorPermission() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(MetricsCommand.register());

        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("metrics", source));
    }

    @Test
    void formatsTimersDistributionsAndRatiosUnderAHeaderAndReportsEmptyAfterClear() throws Exception {
        Timer timer = Store.getTimer(MetricsCommandTest.class, "sampleTimer");
        timer.record(1_000);
        timer.record(3_000);
        Distribution distribution = Store.getDistribution(MetricsCommandTest.class, "sampleDistribution");
        distribution.record(50);
        distribution.record(10);
        distribution.record(100);
        distribution.record(20);
        Store.getRatio(MetricsCommandTest.class, "sampleRatio").success();
        Store.getRatio(MetricsCommandTest.class, "sampleRatio").success();
        Store.getRatio(MetricsCommandTest.class, "otherRatio").success();
        Store.getRatio(MetricsCommandTest.class, "otherRatio").failure();
        int seeded = Store.timers().size() + Store.distributions().size() + Store.ratios().size();
        List<String> messages = new ArrayList<>();
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.hasPermission(2)).thenReturn(true);
        doAnswer(invocation -> {
            Supplier<Component> message = invocation.getArgument(0);
            messages.add(message.get().getString());
            return null;
        }).when(source).sendSuccess(any(), eq(false));
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(MetricsCommand.register());

        dispatcher.execute("metrics", source);

        assertEquals("=== RiverTale Metrics ===", messages.get(0));
        assertEquals(4, seeded);
        assertEquals(1 + seeded, messages.size());
        assertTrue(messages.contains("MetricsCommandTest_sampleTimer: 2 calls | avg=2.0us min=1.0us max=3.0us med=1.0us"));
        assertTrue(messages.contains("MetricsCommandTest_sampleDistribution: 4 samples | avg=45.0 min=10.0 max=100.0 med=35.0"));
        assertTrue(messages.contains("MetricsCommandTest_sampleRatio: 2/2 (100.0%)"));
        assertTrue(messages.contains("MetricsCommandTest_otherRatio: 1/2 (50.0%)"));
        Store.clear();
        messages.clear();
        dispatcher.execute("metrics", source);
        assertEquals(List.of("No metrics recorded yet."), messages);
    }

    @Test
    void disabledMetricsDoNotStoreLengthSamples() {
        CommonConfig.set(new CommonConfig(8, 1, 1, false, true, 16, 5, 10, SourceConfig.DEFAULT, CacheConfig.DEFAULT));
        Distribution lengths = Store.getDistribution(MetricsCommandTest.class, "sampleDistribution");
        lengths.record(1234);

        assertEquals(0, lengths.snapshot().count());
        assertTrue(Store.distributions().isEmpty());
    }
}
