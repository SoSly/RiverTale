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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.levelgen.Heightmap;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.Shape;

public class RiverBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SHARPNESS = 3;
    private static final double BLEND_THRESHOLD = 0.3;
    private static final int INFLUENCE_RADIUS = 120;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    private static Holder<Biome> riverBiome;

    private RiverBuilder() {}

    public static void init(Holder<Biome> river) {
        riverBiome = river;
    }

    public static void shutdown() {
        riverBiome = null;
    }

    public static void generateRiverMap(ChunkAccess chunk) {
        RegionPos center = new RegionPos(chunk.getPos().getMiddleBlockPosition(0));
        CellCache cellCache = CellCache.get();
        SampleCache sampleCache = SampleCache.get();
        RegionCache regionCache = RegionCache.get();

        regionCache.getOrCompute(center, cellCache, sampleCache);

        for (Direction dir : Direction.D8) {
            regionCache.getOrCompute(center.relative(dir), cellCache, sampleCache);
        }
    }

    private record ColumnResult(int y, boolean isRiverbed, Integer waterY) {}

    public static void shape(ChunkAccess chunk) {
        Timer.Record shapeTimer = Store.getTimer(RiverBuilder.class, "shape").start();
        ChunkPos chunkPos = chunk.getPos();

        Set<Cell> contributors = findContributingCells(chunkPos);
        if (contributors.isEmpty()) {
            shapeTimer.stop();
            return;
        }

        Map<Cell, Shape[][]> opinions = collectOpinions(contributors, chunkPos);
        if (opinions.isEmpty()) {
            shapeTimer.stop();
            return;
        }

        ColumnResult[][] results = combinePerColumn(chunk, opinions);
        applyToChunk(chunk, results);

        shapeTimer.stop();
    }

    private static Set<Cell> findContributingCells(ChunkPos chunkPos) {
        CellPos minCell = new CellPos(new BlockPos(
            chunkPos.getMinBlockX() - INFLUENCE_RADIUS,
            0,
            chunkPos.getMinBlockZ() - INFLUENCE_RADIUS
        ));
        CellPos maxCell = new CellPos(new BlockPos(
            chunkPos.getMaxBlockX() + INFLUENCE_RADIUS,
            0,
            chunkPos.getMaxBlockZ() + INFLUENCE_RADIUS
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
        RegionCache regionCache = RegionCache.get();
        CellCache cellCache = CellCache.get();
        SampleCache sampleCache = SampleCache.get();

        for (Cell cell : contributors) {
            Region region = regionCache.getOrCompute(cell.pos().getRegion(), cellCache, sampleCache);
            Watershed watershed = region.watershed();
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

        for (Shape shape : shapes) {
            if (shape.isRiverbed()) {
                riverbeds.add(shape);
            } else {
                embankments.add(shape);
            }
        }

        int y;
        double confidence;
        boolean isRiverbed = !riverbeds.isEmpty();
        Integer waterY = null;

        if (isRiverbed) {
            double[] result = powerWeightedAverage(riverbeds);
            y = (int) result[0];
            confidence = result[1];
            waterY = combineWaterY(riverbeds);
        } else {
            double[] result = weightedAverage(embankments);
            y = (int) result[0];
            confidence = result[1];
        }

        int blendedY = blendTowardVanilla(y, vanillaY, confidence, isRiverbed);
        return new ColumnResult(blendedY, isRiverbed, waterY);
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

        // Embankments: asymmetric blending
        // - Raising terrain (building embankment): use full blend out to 120 blocks
        // - Lowering terrain: only lower when confidence is high (close to river)
        int blended = (int) Math.round(vanillaY + (riverY - vanillaY) * confidence);

        if (blended >= vanillaY) {
            // Raising terrain - use full blend to build embankment
            return blended;
        } else {
            // Lowering terrain - only do it when very confident (close to river)
            if (confidence > 0.7) {
                return blended;
            }
            // Otherwise, minimal lowering - mostly stay at vanilla
            double reducedConfidence = confidence / 0.7 * 0.3;  // Scale 0-0.7 to 0-0.3
            return (int) Math.round(vanillaY + (riverY - vanillaY) * reducedConfidence);
        }
    }

    private static final int DEBUG_CHUNK_X = 244;
    private static final int DEBUG_CHUNK_Z = 118;

    private static void applyToChunk(ChunkAccess chunk, ColumnResult[][] results) {
        ChunkPos pos = chunk.getPos();
        boolean debug = pos.x == DEBUG_CHUNK_X && pos.z == DEBUG_CHUNK_Z;

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (results[x][z] == null) {
                    continue;
                }
                int vanillaY = findSurfaceHeight(chunk, x, z);
                int targetY = results[x][z].y();
                Integer waterY = results[x][z].waterY();
                boolean isRiverbed = results[x][z].isRiverbed();

                if (debug && isRiverbed) {
                    int worldX = pos.getMinBlockX() + x;
                    int worldZ = pos.getMinBlockZ() + z;
                    int depth = waterY != null ? waterY - targetY : 0;
                    LOGGER.info("RIVERBED ({},{}) vanillaY={} targetY={} waterY={} depth={}",
                        worldX, worldZ, vanillaY, targetY, waterY, depth);
                }

                applySurface(chunk, x, z, vanillaY, targetY);

                if (waterY != null) {
                    fillWater(chunk, x, z, targetY, waterY);
                }

                if (isRiverbed && riverBiome != null) {
                    applyRiverBiome(chunk, x, z, waterY != null ? waterY : targetY);
                }
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
            if (!state.isAir()) {
                return y;
            }
        }
        return minY;
    }

    private static void applySurface(ChunkAccess chunk, int localX, int localZ, int vanillaY, int targetY) {
        if (targetY == vanillaY) {
            return;
        }

        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        if (targetY < vanillaY) {
            for (int y = vanillaY; y > targetY; y--) {
                setBlockDirect(chunk, localX, y, localZ, AIR);
                oceanFloor.update(localX, y, localZ, AIR);
                worldSurface.update(localX, y, localZ, AIR);
            }
        } else {
            for (int y = vanillaY + 1; y <= targetY; y++) {
                setBlockDirect(chunk, localX, y, localZ, STONE);
                oceanFloor.update(localX, y, localZ, STONE);
                worldSurface.update(localX, y, localZ, STONE);
            }
        }
    }

    private static void fillWater(ChunkAccess chunk, int localX, int localZ, int surfaceY, int waterY) {
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);

        for (int y = surfaceY + 1; y <= waterY; y++) {
            setBlockDirect(chunk, localX, y, localZ, WATER);
            oceanFloor.update(localX, y, localZ, WATER);
            worldSurface.update(localX, y, localZ, WATER);
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
