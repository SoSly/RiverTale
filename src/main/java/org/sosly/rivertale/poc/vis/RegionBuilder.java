package org.sosly.rivertale.poc.vis;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.poc.vis.metrics.Store;
import org.sosly.rivertale.poc.vis.metrics.Timer;

public class RegionBuilder {
    private static final Direction[] D4 = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    private static final Direction[] D8 = {
        Direction.NORTH, Direction.NORTHEAST, Direction.EAST, Direction.SOUTHEAST,
        Direction.SOUTH, Direction.SOUTHWEST, Direction.WEST, Direction.NORTHWEST
    };

    private final OceanFinder finder;
    private final int regionX;
    private final int regionZ;
    private final int regionSize;
    private final int minChunkX;
    private final int maxChunkX;
    private final int minChunkZ;
    private final int maxChunkZ;

    public RegionBuilder(OceanFinder finder, int regionX, int regionZ, int regionSize) {
        this.finder = finder;
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.regionSize = regionSize;
        int minX = regionX * regionSize;
        int minZ = regionZ * regionSize;
        this.minChunkX = minX >> 4;
        this.maxChunkX = (minX + regionSize) >> 4;
        this.minChunkZ = minZ >> 4;
        this.maxChunkZ = (minZ + regionSize) >> 4;
    }

    public Region build(RegionType type) {
        Timer.Record timer = Store.getTimer(RegionBuilder.class, "build").start();
        Region region = new Region(regionX, regionZ, type);

        List<OceanBoundary> startingBoundaries = findAllStartingBoundaries();
        if (startingBoundaries.isEmpty()) {
            timer.stop();
            return region;
        }

        Set<Long> visited = new HashSet<>();
        Deque<Long> queue = new ArrayDeque<>();

        for (OceanBoundary start : startingBoundaries) {
            long startKey = chunkKey(start.landChunkX(), start.landChunkZ());
            if (!visited.contains(startKey)) {
                queue.add(startKey);
                visited.add(startKey);
            }
        }

        while (!queue.isEmpty()) {
            long current = queue.poll();
            int chunkX = (int) (current >> 32);
            int chunkZ = (int) current;

            for (Direction dir : D4) {
                int nx = chunkX + dir.dx;
                int nz = chunkZ + dir.dz;

                if (finder.isOceanChunk(nx, nz)) {
                    region.put(new OceanBoundary(
                        OceanFinder.chunkCenterX(chunkX),
                        OceanFinder.chunkCenterZ(chunkZ),
                        OceanFinder.chunkCenterX(nx),
                        OceanFinder.chunkCenterZ(nz)
                    ));
                }
            }

            for (Direction dir : D8) {
                int nx = chunkX + dir.dx;
                int nz = chunkZ + dir.dz;

                if (!inBounds(nx, nz)) {
                    continue;
                }

                if (finder.isOceanChunk(nx, nz)) {
                    continue;
                }

                long neighborKey = chunkKey(nx, nz);
                if (!visited.contains(neighborKey) && isCoastal(nx, nz)) {
                    visited.add(neighborKey);
                    queue.add(neighborKey);
                }
            }
        }

        region.setVisitedChunks(visited.size());
        timer.stop();
        return region;
    }

    private static final int GRID_SIZE = 8;

    private List<OceanBoundary> findAllStartingBoundaries() {
        List<OceanBoundary> boundaries = new ArrayList<>();

        int gridWidth = maxChunkX - minChunkX;
        int gridHeight = maxChunkZ - minChunkZ;
        int cellWidth = gridWidth / GRID_SIZE;
        int cellHeight = gridHeight / GRID_SIZE;

        boolean[][] isOcean = new boolean[GRID_SIZE][GRID_SIZE];
        int[][] sampleX = new int[GRID_SIZE][GRID_SIZE];
        int[][] sampleZ = new int[GRID_SIZE][GRID_SIZE];

        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                sampleX[gx][gz] = minChunkX + (gx * cellWidth) + (cellWidth / 2);
                sampleZ[gx][gz] = minChunkZ + (gz * cellHeight) + (cellHeight / 2);
                isOcean[gx][gz] = finder.isOceanChunk(sampleX[gx][gz], sampleZ[gx][gz]);
            }
        }

        for (int gx = 0; gx < GRID_SIZE; gx++) {
            for (int gz = 0; gz < GRID_SIZE; gz++) {
                if (gx < GRID_SIZE - 1 && isOcean[gx][gz] != isOcean[gx + 1][gz]) {
                    OceanBoundary b = binarySearchBetween(
                        sampleX[gx][gz], sampleZ[gx][gz], isOcean[gx][gz],
                        sampleX[gx + 1][gz], sampleZ[gx + 1][gz]
                    );
                    if (b != null) {
                        boundaries.add(b);
                    }
                }
                if (gz < GRID_SIZE - 1 && isOcean[gx][gz] != isOcean[gx][gz + 1]) {
                    OceanBoundary b = binarySearchBetween(
                        sampleX[gx][gz], sampleZ[gx][gz], isOcean[gx][gz],
                        sampleX[gx][gz + 1], sampleZ[gx][gz + 1]
                    );
                    if (b != null) {
                        boundaries.add(b);
                    }
                }
            }
        }

        return boundaries;
    }

    private OceanBoundary binarySearchBetween(int x1, int z1, boolean isOcean1, int x2, int z2) {
        int oceanX, oceanZ, landX, landZ;
        if (isOcean1) {
            oceanX = x1;
            oceanZ = z1;
            landX = x2;
            landZ = z2;
        } else {
            oceanX = x2;
            oceanZ = z2;
            landX = x1;
            landZ = z1;
        }

        while (Math.abs(oceanX - landX) > 1 || Math.abs(oceanZ - landZ) > 1) {
            int midX = (oceanX + landX) / 2;
            int midZ = (oceanZ + landZ) / 2;

            if (finder.isOceanChunk(midX, midZ)) {
                oceanX = midX;
                oceanZ = midZ;
            } else {
                landX = midX;
                landZ = midZ;
            }
        }

        return new OceanBoundary(
            OceanFinder.chunkCenterX(landX),
            OceanFinder.chunkCenterZ(landZ),
            OceanFinder.chunkCenterX(oceanX),
            OceanFinder.chunkCenterZ(oceanZ)
        );
    }

    private boolean inBounds(int chunkX, int chunkZ) {
        return chunkX >= minChunkX && chunkX < maxChunkX
            && chunkZ >= minChunkZ && chunkZ < maxChunkZ;
    }

    private boolean isCoastal(int chunkX, int chunkZ) {
        if (finder.isOceanChunk(chunkX, chunkZ)) {
            return false;
        }
        for (Direction dir : D4) {
            if (finder.isOceanChunk(chunkX + dir.dx, chunkZ + dir.dz)) {
                return true;
            }
        }
        return false;
    }

    private static long chunkKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }
}
