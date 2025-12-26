package org.sosly.rivertale.terrain;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.Cache;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

public final class Oceans {
    private static final int CHUNK_SIZE = 16;

    private Oceans() {
    }

    public static Set<OceanBoundary> boundaries(RegionPos region, Cache<Cell> cellCache, Cache<Sample> sampleCache) {
        Timer.Record timer = Store.getTimer(Oceans.class, "boundaries").start();
        Set<OceanBoundary> result = new HashSet<>();
        Set<Long> visitedChunks = new HashSet<>();
        ArrayDeque<ChunkPos> queue = new ArrayDeque<>();

        int regionSize = CommonConfig.get().regionSize();
        int minBlockX = region.getMinBlockX();
        int minBlockZ = region.getMinBlockZ();
        int minChunkX = minBlockX >> 4;
        int minChunkZ = minBlockZ >> 4;
        int maxChunkX = minChunkX + (regionSize >> 4);
        int maxChunkZ = minChunkZ + (regionSize >> 4);

        CellPos minCell = region.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();

        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                CellPos cellPos = new CellPos(minCell.x() + x, minCell.z() + z);
                Cell cell = cellCache.getOrCompute(cellPos.x(), cellPos.z());

                if (cell.sample().isOcean()) {
                    continue;
                }

                for (Direction dir : Direction.D4) {
                    CellPos neighborPos = cellPos.relative(dir);
                    Cell neighbor = cellCache.getOrCompute(neighborPos.x(), neighborPos.z());

                    if (neighbor.sample().isOcean()) {
                        OceanBoundary seed = refineCellBoundary(cellPos, neighborPos, sampleCache);
                        long key = seed.land().toLong();
                        if (!visitedChunks.contains(key)) {
                            visitedChunks.add(key);
                            queue.add(seed.land());
                        }
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            ChunkPos current = queue.poll();

            for (Direction dir : Direction.D4) {
                ChunkPos neighbor = new ChunkPos(current.x + dir.dx, current.z + dir.dz);
                if (isOceanChunk(neighbor, sampleCache)) {
                    result.add(new OceanBoundary(current, neighbor));
                }
            }

            for (Direction dir : Direction.D8) {
                ChunkPos neighbor = new ChunkPos(current.x + dir.dx, current.z + dir.dz);

                if (neighbor.x < minChunkX || neighbor.x >= maxChunkX
                        || neighbor.z < minChunkZ || neighbor.z >= maxChunkZ) {
                    continue;
                }

                if (isOceanChunk(neighbor, sampleCache)) {
                    continue;
                }

                long key = neighbor.toLong();
                if (!visitedChunks.contains(key) && isCoastalChunk(neighbor, sampleCache)) {
                    visitedChunks.add(key);
                    queue.add(neighbor);
                }
            }
        }

        timer.stop();
        return result;
    }

    private static boolean isCoastalChunk(ChunkPos chunk, Cache<Sample> cache) {
        for (Direction dir : Direction.D4) {
            ChunkPos neighbor = new ChunkPos(chunk.x + dir.dx, chunk.z + dir.dz);
            if (isOceanChunk(neighbor, cache)) {
                return true;
            }
        }
        return false;
    }

    private static OceanBoundary refineCellBoundary(CellPos land, CellPos ocean, Cache<Sample> cache) {
        ChunkPos landChunk = new ChunkPos(land.getMiddleBlockX() >> 4, land.getMiddleBlockZ() >> 4);
        ChunkPos oceanChunk = new ChunkPos(ocean.getMiddleBlockX() >> 4, ocean.getMiddleBlockZ() >> 4);
        return refineToChunkBoundary(landChunk, oceanChunk, cache);
    }

    public static OceanBoundary nearest(ChunkPos from, Cache<Sample> cache, int gridSize) {
        Timer.Record timer = Store.getTimer(Oceans.class, "findNearest").start();

        int gridSizeInChunks = Math.max(1, gridSize / CHUNK_SIZE);
        ChunkPos ocean = spiralSearchCoarse(from, cache, gridSizeInChunks);
        OceanBoundary output = refineToChunkBoundary(from, ocean, cache);

        timer.stop();
        return output;
    }

    private static ChunkPos spiralSearchCoarse(ChunkPos center, Cache<Sample> cache, int gridSizeInChunks) {
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;

        while (true) {
            ChunkPos sample = new ChunkPos(
                center.x + (x * gridSizeInChunks),
                center.z + (z * gridSizeInChunks)
            );

            if (isOceanChunk(sample, cache)) {
                return sample;
            }

            if ((x == z) || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                int temp = dx;
                dx = -dz;
                dz = temp;
            }

            x += dx;
            z += dz;
        }
    }

    private static OceanBoundary refineToChunkBoundary(ChunkPos land, ChunkPos ocean, Cache<Sample> cache) {
        int originalDiffX = Math.abs(ocean.x - land.x);
        int originalDiffZ = Math.abs(ocean.z - land.z);

        int landX = land.x;
        int landZ = land.z;
        int oceanX = ocean.x;
        int oceanZ = ocean.z;

        while (true) {
            int diffX = Math.abs(oceanX - landX);
            int diffZ = Math.abs(oceanZ - landZ);

            if (diffX <= 1 && diffZ <= 1) {
                break;
            }

            ChunkPos mid = new ChunkPos((landX + oceanX) / 2, (landZ + oceanZ) / 2);

            if (isOceanChunk(mid, cache)) {
                oceanX = mid.x;
                oceanZ = mid.z;
            } else {
                landX = mid.x;
                landZ = mid.z;
            }
        }

        ChunkPos refinedLand = new ChunkPos(landX, landZ);
        ChunkPos refinedOcean = new ChunkPos(oceanX, oceanZ);

        int finalDiffX = Math.abs(oceanX - landX);
        int finalDiffZ = Math.abs(oceanZ - landZ);

        if (finalDiffX == 1 && finalDiffZ == 1) {
            if (originalDiffX >= originalDiffZ) {
                return refineAlongX(refinedLand, refinedOcean, cache);
            } else {
                return refineAlongZ(refinedLand, refinedOcean, cache);
            }
        }

        return new OceanBoundary(refinedLand, refinedOcean);
    }

    private static OceanBoundary refineAlongX(ChunkPos land, ChunkPos ocean, Cache<Sample> cache) {
        int step = Integer.compare(ocean.x, land.x);
        ChunkPos testX = new ChunkPos(land.x + step, land.z);

        if (isOceanChunk(testX, cache)) {
            return new OceanBoundary(land, testX);
        }

        int stepZ = Integer.compare(ocean.z, land.z);
        ChunkPos testZ = new ChunkPos(land.x, land.z + stepZ);

        if (isOceanChunk(testZ, cache)) {
            return new OceanBoundary(land, testZ);
        }

        return new OceanBoundary(testX, ocean);
    }

    private static OceanBoundary refineAlongZ(ChunkPos land, ChunkPos ocean, Cache<Sample> cache) {
        int step = Integer.compare(ocean.z, land.z);
        ChunkPos testZ = new ChunkPos(land.x, land.z + step);

        if (isOceanChunk(testZ, cache)) {
            return new OceanBoundary(land, testZ);
        }

        int stepX = Integer.compare(ocean.x, land.x);
        ChunkPos testX = new ChunkPos(land.x + stepX, land.z);

        if (isOceanChunk(testX, cache)) {
            return new OceanBoundary(land, testX);
        }

        return new OceanBoundary(testZ, ocean);
    }

    public static boolean isOceanChunk(ChunkPos pos, Cache<Sample> cache) {
        return cache.getOrCompute(pos.getMiddleBlockX(), pos.getMiddleBlockZ()).isOcean();
    }
}
