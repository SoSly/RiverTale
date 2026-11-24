package org.sosly.rivertale.river;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiverRegionCacheTest {
    private static final RegionPos CENTER = new RegionPos(0, 0);
    private static final RegionPos UNRELATED = new RegionPos(4, 0);

    @Test
    void backgroundClaimsTheNineRegionsOnceAndChunkRequestsWaitOnlyForTheirOwnBuild() throws Exception {
        CountDownLatch building = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch requesting = new CountDownLatch(1);
        AtomicInteger neighborhoodBuilds = new AtomicInteger();
        RiverRegionCache cache = new RiverRegionCache(roots -> {
            if (roots.contains(CENTER)) {
                neighborhoodBuilds.incrementAndGet();
                building.countDown();
                assertEquals(Set.copyOf(CENTER.neighborhood()), roots);
                await(finished);
            }
            return built(roots);
        });
        ExecutorService worldgen = Executors.newFixedThreadPool(2);
        try {
            RiverRegion unaffected = cache.getOrBuild(UNRELATED);
            cache.prepareNearby(CENTER);
            await(building);
            assertNull(cache.getReady(CENTER));

            Future<RiverRegion> chunk = worldgen.submit(() -> {
                requesting.countDown();
                return cache.getOrBuild(CENTER);
            });
            await(requesting);
            assertThrows(TimeoutException.class, () -> chunk.get(100, TimeUnit.MILLISECONDS));
            assertSame(unaffected, worldgen.submit(() -> cache.getOrBuild(UNRELATED)).get(5, TimeUnit.SECONDS));

            finished.countDown();
            assertSame(chunk.get(5, TimeUnit.SECONDS), cache.getReady(CENTER));
            for (RegionPos pos : CENTER.neighborhood()) {
                assertEquals(pos, cache.getOrBuild(pos).region().pos());
            }
            assertEquals(1, neighborhoodBuilds.get());
        } finally {
            finished.countDown();
            worldgen.shutdownNow();
            cache.shutdown();
        }
    }

    @Test
    void aColdChunkBuildRunsOnItsCallerAndAFailureCanBeRetried() {
        AtomicInteger attempts = new AtomicInteger();
        Thread worldgen = Thread.currentThread();
        RiverRegionCache cache = new RiverRegionCache(roots -> {
            assertSame(worldgen, Thread.currentThread());
            assertEquals(Set.of(CENTER), roots);
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("Build failed");
            }
            return built(roots);
        });
        try {
            assertThrows(IllegalStateException.class, () -> cache.getOrBuild(CENTER));
            assertNull(cache.getReady(CENTER));
            assertSame(cache.getOrBuild(CENTER), cache.getReady(CENTER));
            assertEquals(2, attempts.get());
        } finally {
            cache.shutdown();
        }
    }

    private static Map<RegionPos, RiverRegion> built(Set<RegionPos> roots) {
        Map<RegionPos, RiverRegion> result = new HashMap<>();
        for (RegionPos pos : roots) {
            result.put(pos, new RiverRegion(new Region(pos, RegionType.OCEANIC), Map.of(), List.of(), List.of()));
        }
        return result;
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new AssertionError(failure);
        }
    }
}
