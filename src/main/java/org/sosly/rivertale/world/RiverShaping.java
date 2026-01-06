package org.sosly.rivertale.world;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import org.slf4j.Logger;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.Heightmap;
import org.sosly.rivertale.capability.FlowDirectionCapability;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.river.WatershedCache;
import org.sosly.rivertale.terrain.Shape;

public class RiverShaping {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SHARPNESS = 3;
    private static final double BLEND_THRESHOLD = 0.3;
    private static int influenceRadius() {
        return CommonConfig.get().embankmentRadius();
    }

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    private static Holder<Biome> riverBiome;

    private RiverShaping() {}

    public static void init(Holder<Biome> river) {
        riverBiome = river;
    }

    public static void shutdown() {
        riverBiome = null;
    }

    public static void generateRiverMap(ChunkAccess chunk) {
        Timer.Record timer = Store.getTimer(RiverShaping.class, "generateRiverMap").start();
        RegionPos center = new RegionPos(chunk.getPos().getMiddleBlockPosition(0));
        CellCache cellCache = CellCache.get();
        SampleCache sampleCache = SampleCache.get();
        RegionCache regionCache = RegionCache.get();

        Set<RegionPos> loadedRegions = new HashSet<>();

        regionCache.getOrCompute(center, cellCache, sampleCache);
        loadedRegions.add(center);

        for (Direction dir : Direction.D8) {
            RegionPos neighbor = center.relative(dir);
            regionCache.getOrCompute(neighbor, cellCache, sampleCache);
            loadedRegions.add(neighbor);
        }

        WatershedCache.get().finalizeReady(loadedRegions);
        timer.stop();
    }

    private record ColumnResult(int y, boolean isRiverbed, Integer waterY, Integer flowLevel, Direction flowDirection, boolean preserveBiome) {}

    public static void shape(ChunkAccess chunk) {
        Timer.Record timer = Store.getTimer(RiverShaping.class, "shape").start();
        ChunkPos chunkPos = chunk.getPos();

        Set<Cell> contributors = findContributingCells(chunkPos);
        if (contributors.isEmpty()) {
            timer.stop();
            return;
        }

        Map<Cell, Shape[][]> opinions = collectOpinions(contributors, chunkPos);
        if (opinions.isEmpty()) {
            timer.stop();
            return;
        }

        ColumnResult[][] results = combinePerColumn(chunk, opinions);
        applyToChunk(chunk, results);

        timer.stop();
    }

    private static Set<Cell> findContributingCells(ChunkPos chunkPos) {
        int radius = influenceRadius();
        CellPos minCell = new CellPos(new BlockPos(
            chunkPos.getMinBlockX() - radius,
            0,
            chunkPos.getMinBlockZ() - radius
        ));
        CellPos maxCell = new CellPos(new BlockPos(
            chunkPos.getMaxBlockX() + radius,
            0,
            chunkPos.getMaxBlockZ() + radius
        ));

        Set<Cell> contributors = new HashSet<>();
        CellCache cellCache = CellCache.get();

        for (int cx = minCell.x(); cx <= maxCell.x(); cx++) {
            for (int cz = minCell.z(); cz <= maxCell.z(); cz++) {
                Cell cell = cellCache.getOrCompute(new CellPos(cx, cz));
                Feature feature = cell.feature();
                if (feature != Feature.DIVIDE && feature != Feature.DEFAULT) {
                    contributors.add(cell);
                }
            }
        }

        return contributors;
    }

    private static Map<Cell, Shape[][]> collectOpinions(Set<Cell> contributors, ChunkPos chunkPos) {
        Map<Cell, Shape[][]> opinions = new HashMap<>();
        WatershedCache watershedCache = WatershedCache.get();

        for (Cell cell : contributors) {
            Watershed watershed = watershedCache.getWatershed(cell.pos());
            if (watershed == null) {
                continue;
            }

            Shape[][] grid = cell.feature().handler.shape(cell, watershed, chunkPos);
            if (grid != null) {
                opinions.put(cell, grid);
            }
        }

        return opinions;
    }

    private static ColumnResult[][] combinePerColumn(ChunkAccess chunk, Map<Cell, Shape[][]> opinions) {
        ColumnResult[][] results = new ColumnResult[16][16];

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                List<Shape> shapes = new ArrayList<>();
                for (Shape[][] grid : opinions.values()) {
                    if (grid[x][z] != null) {
                        shapes.add(grid[x][z]);
                    }
                }

                if (shapes.isEmpty()) {
                    continue;
                }

                int vanillaY = findSurfaceHeight(chunk, x, z);
                results[x][z] = combineShapes(shapes, vanillaY);
            }
        }

        return results;
    }

    private static ColumnResult combineShapes(List<Shape> shapes, int vanillaY) {
        List<Shape> riverbeds = new ArrayList<>();
        List<Shape> embankments = new ArrayList<>();
        boolean preserveBiome = false;

        for (Shape shape : shapes) {
            if (shape.isRiverbed()) {
                riverbeds.add(shape);
            } else {
                embankments.add(shape);
            }
            if (shape.preserveBiome()) {
                preserveBiome = true;
            }
        }

        int y;
        double confidence;
        boolean isRiverbed = !riverbeds.isEmpty();
        Integer waterY = null;
        Integer flowLevel = null;
        Direction flowDirection = null;

        if (isRiverbed) {
            double[] result = powerWeightedAverage(riverbeds);
            y = (int) result[0];
            confidence = result[1];
            waterY = combineWaterY(riverbeds);
            flowLevel = combineFlowLevel(riverbeds);
            flowDirection = combineFlowDirection(riverbeds);
        } else {
            double[] result = weightedAverage(embankments);
            y = (int) result[0];
            confidence = result[1];
        }

        int blendedY = blendTowardVanilla(y, vanillaY, confidence, isRiverbed);
        return new ColumnResult(blendedY, isRiverbed, waterY, flowLevel, flowDirection, preserveBiome);
    }

    private static Integer combineWaterY(List<Shape> riverbeds) {
        Integer maxWaterY = null;
        for (Shape shape : riverbeds) {
            if (shape.waterY() != null) {
                if (maxWaterY == null || shape.waterY() > maxWaterY) {
                    maxWaterY = shape.waterY();
                }
            }
        }
        return maxWaterY;
    }

    private static Integer combineFlowLevel(List<Shape> riverbeds) {
        Integer minFlowLevel = null;
        for (Shape shape : riverbeds) {
            if (shape.flowLevel() != null) {
                if (minFlowLevel == null || shape.flowLevel() < minFlowLevel) {
                    minFlowLevel = shape.flowLevel();
                }
            }
        }
        return minFlowLevel;
    }

    private static Direction combineFlowDirection(List<Shape> riverbeds) {
        Direction best = null;
        double bestWeight = 0;
        for (Shape shape : riverbeds) {
            if (shape.flowDirection() != null && shape.weight() > bestWeight) {
                best = shape.flowDirection();
                bestWeight = shape.weight();
            }
        }
        return best;
    }

    private static double[] powerWeightedAverage(List<Shape> shapes) {
        double sumY = 0;
        double sumWeight = 0;
        double maxWeight = 0;

        for (Shape shape : shapes) {
            double adjusted = Math.pow(shape.weight(), SHARPNESS);
            sumY += shape.y() * adjusted;
            sumWeight += adjusted;
            maxWeight = Math.max(maxWeight, shape.weight());
        }

        int y = (int) Math.round(sumY / sumWeight);
        return new double[]{y, maxWeight};
    }

    private static double[] weightedAverage(List<Shape> shapes) {
        double sumY = 0;
        double sumWeight = 0;
        double maxWeight = 0;

        for (Shape shape : shapes) {
            sumY += shape.y() * shape.weight();
            sumWeight += shape.weight();
            maxWeight = Math.max(maxWeight, shape.weight());
        }

        int y = (int) Math.round(sumY / sumWeight);
        return new double[]{y, maxWeight};
    }

    private static int blendTowardVanilla(int riverY, int vanillaY, double confidence, boolean isRiverbed) {
        if (isRiverbed) {
            // Riverbeds need strong control to ensure proper water containment
            if (confidence >= BLEND_THRESHOLD) {
                return riverY;
            }
            double t = confidence / BLEND_THRESHOLD;
            return (int) Math.round(vanillaY + (riverY - vanillaY) * t);
        }

        int blended = (int) Math.round(vanillaY + (riverY - vanillaY) * confidence);

        if (blended >= vanillaY) {
            return blended;
        } else {
            double smoothConfidence = confidence * confidence;
            return (int) Math.round(vanillaY + (riverY - vanillaY) * smoothConfidence);
        }
    }

    private static final int DEBUG_CHUNK_X = 244;
    private static final int DEBUG_CHUNK_Z = 118;

    private static void applyToChunk(ChunkAccess chunk, ColumnResult[][] results) {
        ChunkPos pos = chunk.getPos();
        boolean debug = pos.x == DEBUG_CHUNK_X && pos.z == DEBUG_CHUNK_Z;

        Direction[][] flowDirections = new Direction[16][16];
        boolean hasFlowData = false;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (results[x][z] == null) {
                    continue;
                }
                int vanillaY = findSurfaceHeight(chunk, x, z);
                int targetY = results[x][z].y();
                Integer waterY = results[x][z].waterY();
                Integer flowLevel = results[x][z].flowLevel();
                boolean isRiverbed = results[x][z].isRiverbed();
                Direction flowDirection = results[x][z].flowDirection();

                if (debug && isRiverbed) {
                    int worldX = pos.getMinBlockX() + x;
                    int worldZ = pos.getMinBlockZ() + z;
                    int depth = waterY != null ? waterY - targetY : 0;
                    LOGGER.info("RIVERBED ({},{}) vanillaY={} targetY={} waterY={} depth={} flowLevel={}",
                        worldX, worldZ, vanillaY, targetY, waterY, depth, flowLevel);
                }

                applyColumn(chunk, x, z, targetY, waterY, flowLevel);

                if (isRiverbed && riverBiome != null && !results[x][z].preserveBiome()) {
                    applyRiverBiome(chunk, x, z, waterY != null ? waterY : targetY);
                }

                if (isRiverbed && flowDirection != null) {
                    flowDirections[x][z] = flowDirection;
                    hasFlowData = true;
                }
            }
        }

        if (hasFlowData) {
            FlowDirectionCapability.storePending(pos, flowDirections);
            if (chunk instanceof LevelChunk levelChunk) {
                FlowDirectionCapability.get(levelChunk);
                levelChunk.setUnsaved(true);
            }
        }
    }

    private static int findSurfaceHeight(ChunkAccess chunk, int localX, int localZ) {
        int maxY = chunk.getMaxBuildHeight() - 1;
        int minY = chunk.getMinBuildHeight();
        int worldX = chunk.getPos().getMinBlockX() + localX;
        int worldZ = chunk.getPos().getMinBlockZ() + localZ;

        for (int y = maxY; y >= minY; y--) {
            BlockState state = chunk.getBlockState(new BlockPos(worldX, y, worldZ));
            if (!state.isAir() && !state.is(Blocks.WATER)) {
                return y;
            }
        }
        return minY;
    }

    private static void applyColumn(ChunkAccess chunk, int localX, int localZ, int targetY, Integer waterY, Integer flowLevel) {
        int minY = chunk.getMinBuildHeight();
        int maxY = chunk.getMaxBuildHeight() - 1;

        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        for (int y = minY; y <= targetY; y++) {
            setBlockDirect(chunk, localX, y, localZ, STONE);
            oceanFloor.update(localX, y, localZ, STONE);
            worldSurface.update(localX, y, localZ, STONE);
        }

        int airStart = targetY + 1;
        if (waterY != null && waterY > targetY) {
            int waterEnd = (flowLevel != null) ? waterY + 1 : waterY;
            for (int y = targetY + 1; y <= waterEnd; y++) {
                BlockState waterState;
                if (flowLevel != null && y == waterY + 1) {
                    waterState = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, flowLevel);
                } else {
                    waterState = WATER;
                }
                setBlockDirect(chunk, localX, y, localZ, waterState);
                oceanFloor.update(localX, y, localZ, waterState);
                worldSurface.update(localX, y, localZ, waterState);
            }
            airStart = waterEnd + 1;
        } else if (targetY < WorldSettings.get().seaLevel() - 1) {
            for (int y = targetY + 1; y < WorldSettings.get().seaLevel(); y++) {
                setBlockDirect(chunk, localX, y, localZ, WATER);
                oceanFloor.update(localX, y, localZ, WATER);
                worldSurface.update(localX, y, localZ, WATER);
            }
            airStart = WorldSettings.get().seaLevel();
        }

        for (int y = airStart; y <= maxY; y++) {
            setBlockDirect(chunk, localX, y, localZ, AIR);
            oceanFloor.update(localX, y, localZ, AIR);
            worldSurface.update(localX, y, localZ, AIR);
        }
    }

    private static void applyRiverBiome(ChunkAccess chunk, int localX, int localZ, int surfaceY) {
        int biomeX = localX >> 2;
        int biomeZ = localZ >> 2;

        int minY = chunk.getMinBuildHeight();
        int maxY = surfaceY + 16;

        for (int y = minY; y <= maxY; y += 4) {
            int sectionIndex = chunk.getSectionIndex(y);
            if (sectionIndex < 0 || sectionIndex >= chunk.getSectionsCount()) {
                continue;
            }

            LevelChunkSection section = chunk.getSection(sectionIndex);
            int biomeY = (y & 15) >> 2;

            PalettedContainerRO<Holder<Biome>> biomes = section.getBiomes();
            if (biomes instanceof PalettedContainer<Holder<Biome>> writable) {
                writable.getAndSetUnchecked(biomeX, biomeY, biomeZ, riverBiome);
            }
        }
    }

    private static void setBlockDirect(ChunkAccess chunk, int localX, int y, int localZ, BlockState state) {
        int sectionIndex = chunk.getSectionIndex(y);
        LevelChunkSection section = chunk.getSection(sectionIndex);
        section.setBlockState(localX, y & 15, localZ, state, false);
    }
}
