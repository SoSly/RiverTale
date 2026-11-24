package org.sosly.rivertale.river;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.core.RegionPos;

public final class RiverRegionCache {
    private final ConcurrentMap<RegionPos, CompletableFuture<RiverRegion>> regions = new ConcurrentHashMap<>();
    private final ExecutorService background = Executors.newSingleThreadExecutor(task -> new Thread(task, "RiverTale preparation"));
    private final Function<Set<RegionPos>, Map<RegionPos, RiverRegion>> builder;

    public RiverRegionCache() {
        this(RiverNetwork::build);
    }

    RiverRegionCache(Function<Set<RegionPos>, Map<RegionPos, RiverRegion>> builder) {
        this.builder = builder;
    }

    public RiverRegion getReady(RegionPos pos) {
        CompletableFuture<RiverRegion> region = regions.get(pos);
        if (region == null) {
            return null;
        }
        try {
            return region.getNow(null);
        } catch (CompletionException failure) {
            return null;
        }
    }

    public RiverRegion getOrBuild(RegionPos pos) {
        CompletableFuture<RiverRegion> existing = regions.get(pos);
        if (existing != null) {
            return readyOrWait(existing);
        }

        CompletableFuture<RiverRegion> claimed = new CompletableFuture<>();
        existing = regions.putIfAbsent(pos, claimed);
        if (existing != null) {
            return readyOrWait(existing);
        }
        build(Map.of(pos, claimed));
        return claimed.join();
    }

    private RiverRegion readyOrWait(CompletableFuture<RiverRegion> region) {
        RiverRegion ready = region.getNow(null);
        return ready == null ? region.join() : ready;
    }

    public void prepareNearby(RegionPos center) {
        Collection<RegionPos> nearby = center.neighborhood();
        if (nearby.stream().allMatch(regions::containsKey)) {
            return;
        }
        background.execute(() -> {
            try {
                prepareMissing(nearby);
            } catch (RuntimeException failure) {
                RiverTale.LOGGER.error("River preparation failed near {}", center, failure);
            }
        });
    }

    private void prepareMissing(Collection<RegionPos> nearby) {
        Map<RegionPos, CompletableFuture<RiverRegion>> claimed = new HashMap<>();
        for (RegionPos pos : nearby) {
            CompletableFuture<RiverRegion> candidate = new CompletableFuture<>();
            if (regions.putIfAbsent(pos, candidate) == null) {
                claimed.put(pos, candidate);
            }
        }
        if (!claimed.isEmpty()) {
            build(claimed);
        }
    }

    private void build(Map<RegionPos, CompletableFuture<RiverRegion>> claimed) {
        try {
            Map<RegionPos, RiverRegion> built = builder.apply(Set.copyOf(claimed.keySet()));
            for (RegionPos pos : claimed.keySet()) {
                Objects.requireNonNull(built.get(pos), "Missing built river region " + pos);
            }
            claimed.forEach((pos, region) -> region.complete(built.get(pos)));
        } catch (RuntimeException | Error failure) {
            claimed.forEach((pos, region) -> {
                regions.remove(pos, region);
                region.completeExceptionally(failure);
            });
            throw failure;
        }
    }

    public void shutdown() {
        background.shutdownNow();
        boolean interrupted = false;
        while (!background.isTerminated()) {
            try {
                background.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException failure) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
