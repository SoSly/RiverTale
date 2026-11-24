package org.sosly.rivertale.river;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiFunction;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.core.RegionPos;

public final class RiverRegionCache {
    private final ThreadPoolExecutor background = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        new LinkedBlockingQueue<>(), task -> new Thread(task, "RiverTale preparation"));
    private final ReentrantReadWriteLock buildLock = new ReentrantReadWriteLock();
    private final BiFunction<Set<RegionPos>, Runnable, Map<RegionPos, RiverRegion>> builder;
    private volatile Generation generation = new Generation(CompletableFuture.completedFuture(null));

    public RiverRegionCache() {
        this(RiverNetwork::build);
    }

    RiverRegionCache(BiFunction<Set<RegionPos>, Runnable, Map<RegionPos, RiverRegion>> builder) {
        this.builder = builder;
    }

    public RiverRegion getReady(RegionPos pos) {
        CompletableFuture<RiverRegion> region = generation.regions.get(pos);
        if (region == null) {
            return null;
        }
        try {
            return region.getNow(null);
        } catch (CompletionException | CancellationException failure) {
            return null;
        }
    }

    public RiverRegion getOrBuild(RegionPos pos) {
        while (!background.isShutdown()) {
            Generation current = generation;
            try {
                current.reset.join();
                if (current != generation) {
                    continue;
                }
                RiverRegion ready = getOrBuild(current, pos);
                if (current == generation) {
                    return ready;
                }
            } catch (CompletionException | CancellationException failure) {
                if (current == generation) {
                    throw failure;
                }
            }
        }
        throw new CancellationException("River cache is shut down");
    }

    private RiverRegion getOrBuild(Generation current, RegionPos pos) {
        CompletableFuture<RiverRegion> existing = current.regions.get(pos);
        if (existing != null) {
            return existing.join();
        }
        CompletableFuture<RiverRegion> claimed = new CompletableFuture<>();
        existing = current.regions.putIfAbsent(pos, claimed);
        if (existing != null) {
            return existing.join();
        }
        build(current, Map.of(pos, claimed));
        return claimed.join();
    }

    public synchronized void prepareNearby(RegionPos center) {
        Generation current = generation;
        Collection<RegionPos> nearby = center.neighborhood();
        if (!background.isShutdown() && !nearby.stream().allMatch(current.regions::containsKey)) {
            background.execute(() -> prepare(current, center, nearby));
        }
    }

    private void prepare(Generation current, RegionPos center, Collection<RegionPos> nearby) {
        if (current != generation) {
            return;
        }
        try {
            current.reset.join();
            build(current, claim(current, nearby));
        } catch (RuntimeException failure) {
            RiverTale.LOGGER.error("River preparation failed near {}", center, failure);
        }
    }

    public synchronized void rebuild(RegionPos center, Runnable clearCaches) {
        Generation previous = generation;
        Generation next = new Generation(new CompletableFuture<>());
        generation = next;
        previous.reset.complete(null);
        background.getQueue().clear();
        background.execute(() -> resetAndPrepare(next, center, clearCaches));
    }

    private void resetAndPrepare(Generation current, RegionPos center, Runnable clearCaches) {
        try {
            Map<RegionPos, CompletableFuture<RiverRegion>> claimed;
            buildLock.writeLock().lock();
            try {
                if (current != generation || background.isShutdown()) {
                    return;
                }
                clearCaches.run();
                claimed = claim(current, center.neighborhood());
                current.reset.complete(null);
            } finally {
                buildLock.writeLock().unlock();
            }
            build(current, claimed);
        } catch (RuntimeException | Error failure) {
            current.reset.completeExceptionally(failure);
            RiverTale.LOGGER.error("River rebuild failed near {}", center, failure);
        }
    }

    private Map<RegionPos, CompletableFuture<RiverRegion>> claim(Generation current, Collection<RegionPos> nearby) {
        Map<RegionPos, CompletableFuture<RiverRegion>> claimed = new HashMap<>();
        for (RegionPos pos : nearby) {
            CompletableFuture<RiverRegion> candidate = new CompletableFuture<>();
            if (current.regions.putIfAbsent(pos, candidate) == null) {
                claimed.put(pos, candidate);
            }
        }
        return claimed;
    }

    private void build(Generation current, Map<RegionPos, CompletableFuture<RiverRegion>> claimed) {
        if (claimed.isEmpty()) {
            return;
        }
        buildLock.readLock().lock();
        try {
            checkCurrent(current);
            Map<RegionPos, RiverRegion> built = builder.apply(Set.copyOf(claimed.keySet()), () -> checkCurrent(current));
            checkCurrent(current);
            for (RegionPos pos : claimed.keySet()) {
                Objects.requireNonNull(built.get(pos), "Missing built river region " + pos);
            }
            claimed.forEach((pos, region) -> region.complete(built.get(pos)));
        } catch (CancellationException failure) {
            claimed.forEach((pos, region) -> {
                current.regions.remove(pos, region);
                region.cancel(false);
            });
        } catch (RuntimeException | Error failure) {
            claimed.forEach((pos, region) -> {
                current.regions.remove(pos, region);
                region.completeExceptionally(failure);
            });
            throw failure;
        } finally {
            buildLock.readLock().unlock();
        }
    }

    private void checkCurrent(Generation current) {
        if (current != generation || background.isShutdown()) {
            throw new CancellationException();
        }
    }

    public void shutdown() {
        synchronized (this) {
            background.shutdownNow();
            generation.reset.completeExceptionally(new CancellationException("River cache is shut down"));
        }
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

    private static final class Generation {
        private final ConcurrentMap<RegionPos, CompletableFuture<RiverRegion>> regions = new ConcurrentHashMap<>();
        private final CompletableFuture<Void> reset;

        private Generation(CompletableFuture<Void> reset) {
            this.reset = reset;
        }
    }
}
