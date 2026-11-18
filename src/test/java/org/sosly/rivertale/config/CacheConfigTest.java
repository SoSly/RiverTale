package org.sosly.rivertale.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.util.List;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.client.ClientRegionCache;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.density.SampleProvider;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.region.RegionTypeCache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class CacheConfigTest {
    private CommonConfig original;

    @BeforeEach
    void setUp() {
        original = CommonConfig.get();
        shutdown();
        CommentedConfig data = new TomlParser().parse("""
            regionSize = 8
            cellSize = 1
            samplesPerCell = 1
            [caches]
            samples = 2
            cells = 2
            regions = 2
            regionTypes = 2
            clientRegions = 2
            """);
        CommonConfig.Spec.SPEC.setConfig(data);
        CommonConfig.Spec.load();
        SampleProvider provider = mock(SampleProvider.class);
        when(provider.sampleCore(any(ChunkPos.class))).thenAnswer(invocation ->
            new Sample(invocation.getArgument(0), 0.0, 40, true));
        SampleCache.init(provider);
        CellCache.init();
        RegionTypeCache.init();
        RegionCache.init();
        ClientRegionCache.init();
    }

    @AfterEach
    void tearDown() {
        shutdown();
        CommonConfig.Spec.SPEC.setConfig((CommentedConfig) null);
        CommonConfig.set(original);
    }

    @Test
    void sampleCapacityEvictsTheLeastRecentlyUsedSample() {
        SampleCache cache = SampleCache.get();
        ChunkPos first = new ChunkPos(0, 0);
        ChunkPos second = new ChunkPos(1, 0);
        Sample retained = cache.getOrCompute(first);
        Sample evicted = cache.getOrCompute(second);
        assertSame(retained, cache.getOrCompute(first));
        cache.getOrCompute(new ChunkPos(2, 0));
        assertSame(retained, cache.getOrCompute(first));
        assertNotSame(evicted, cache.getOrCompute(second));
    }

    @Test
    void cellCapacityEvictsTheLeastRecentlyUsedCell() {
        CellCache cache = CellCache.get();
        CellPos first = new CellPos(0, 0);
        CellPos second = new CellPos(1, 0);
        var retained = cache.getOrCompute(first);
        cache.getOrCompute(second);
        assertSame(retained, cache.getOrCompute(first));
        cache.getOrCompute(new CellPos(2, 0));
        assertSame(retained, cache.getIfPresent(first));
        assertNull(cache.getIfPresent(second));
    }

    @Test
    void regionCapacityEvictsTheLeastRecentlyUsedRegion() {
        RegionCache cache = RegionCache.get();
        RegionPos first = new RegionPos(0, 0);
        RegionPos second = new RegionPos(1, 0);
        var retained = cache.getOrCompute(first);
        var evicted = cache.getOrCompute(second);
        assertSame(retained, cache.getOrCompute(first));
        cache.getOrCompute(new RegionPos(2, 0));
        assertSame(retained, cache.getOrCompute(first));
        assertNotSame(evicted, cache.getOrCompute(second));
    }

    @Test
    void regionTypeCapacityRequiresReclassificationAfterEviction() {
        RegionTypeCache cache = RegionTypeCache.get();
        SampleCache samples = SampleCache.get();
        RegionPos first = new RegionPos(0, 0);
        RegionPos second = new RegionPos(1, 0);
        RegionPos third = new RegionPos(2, 0);
        try (MockedStatic<RegionType> types = mockStatic(RegionType.class)) {
            types.when(() -> RegionType.classify(any(RegionPos.class), any(SampleCache.class)))
                .thenReturn(RegionType.INLAND);
            assertEquals(RegionType.INLAND, cache.getOrCompute(first, samples));
            cache.getOrCompute(second, samples);
            cache.getOrCompute(first, samples);
            cache.getOrCompute(third, samples);
            types.when(() -> RegionType.classify(any(RegionPos.class), any(SampleCache.class)))
                .thenReturn(RegionType.COASTAL);
            assertEquals(RegionType.INLAND, cache.getOrCompute(first, samples));
            assertEquals(RegionType.COASTAL, cache.getOrCompute(second, samples));
        }
    }

    @Test
    void clientCapacityEvictsTheLeastRecentlyViewedRegion() {
        ClientRegionCache cache = ClientRegionCache.get();
        var first = clientRegion(0);
        var second = clientRegion(1);
        cache.put(first);
        cache.put(second);
        assertSame(first, cache.get(first.pos()));
        cache.put(clientRegion(2));
        assertSame(first, cache.get(first.pos()));
        assertNull(cache.get(second.pos()));
    }

    private ClientRegionCache.Region clientRegion(int x) {
        return new ClientRegionCache.Region(new RegionPos(x, 0), RegionType.OCEANIC,
            List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private void shutdown() {
        ClientRegionCache.shutdown();
        RegionCache.shutdown();
        RegionTypeCache.shutdown();
        CellCache.shutdown();
        SampleCache.shutdown();
    }
}
