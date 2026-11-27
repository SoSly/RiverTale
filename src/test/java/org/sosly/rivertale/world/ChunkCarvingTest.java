package org.sosly.rivertale.world;

import com.mojang.serialization.Lifecycle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.chunk.UpgradeData;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.PreparedRiverData.CellData;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.CarveProposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChunkCarvingTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Blocks.STONE.defaultBlockState().initCache();
    }

    @Test
    void cutsToWeightedBedWithoutRaisingLowTerrainOrChangingOtherColumns() {
        ChunkPos pos = new ChunkPos(-2, 1);
        ProtoChunk chunk = chunk(pos);
        fillColumn(chunk, 0, 0, 86);
        fillColumn(chunk, 1, 0, 75);
        fillColumn(chunk, 2, 0, 86);
        int x = pos.getMinBlockX();
        int z = pos.getMinBlockZ();
        assertEquals(Blocks.STONE, chunk.getBlockState(new BlockPos(x, 78, z)).getBlock());
        assertEquals(86, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, 0));
        assertEquals(86, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, 0, 0));

        LevelChunkSection noiseSection = chunk.getSection(chunk.getSectionIndex(86));
        noiseSection.acquire();
        try {
            ChunkCarving.apply(chunk, pos, List.of(new CarveProposal(x, z, 78, 1), new CarveProposal(x, z, 82, 0.5),
                new CarveProposal(x + 1, z, 78, 1), new CarveProposal(x + 16, z, 78, 1)));
        } finally {
            noiseSection.release();
        }

        assertEquals(78, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, 0));
        assertEquals(78, chunk.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, 0, 0));
        assertEquals(Blocks.STONE, chunk.getBlockState(new BlockPos(x, 78, z)).getBlock());
        assertTrue(chunk.getBlockState(new BlockPos(x, 79, z)).isAir());
        assertEquals(75, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 1, 0));
        assertEquals(86, chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 2, 0));
    }

    @Test
    void neighboringChunksCarveAContinuousRunInEitherOrder() {
        int size = CommonConfig.get().cellBlocks();
        Cell west = new Cell(new CellPos(0, 0), Set.of()).withCellY(80);
        Cell east = new Cell(new CellPos(1, 0), Set.of()).withCellY(80);
        Watershed watershed = mock(Watershed.class);
        when(watershed.downstream(west.pos())).thenReturn(east.pos());
        when(watershed.upstream(east.pos())).thenReturn(Set.of(west.pos()));
        when(watershed.elevatedCell(west.pos())).thenReturn(west);
        when(watershed.elevatedCell(east.pos())).thenReturn(east);
        for (Cell cell : List.of(west, east)) {
            when(watershed.flow(cell.pos())).thenReturn(OptionalDouble.of(24));
        }
        ChunkPos left = new ChunkPos(size / 16 - 1, size / 32);
        ChunkPos right = new ChunkPos(size / 16, size / 32);

        ProtoChunk leftFirst = carveRunChunk(left, west, east, watershed);
        ProtoChunk rightSecond = carveRunChunk(right, west, east, watershed);
        ProtoChunk rightFirst = carveRunChunk(right, west, east, watershed);
        ProtoChunk leftSecond = carveRunChunk(left, west, east, watershed);

        int z = (size / 2) & 15;
        assertEquals(78, leftFirst.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 15, z));
        assertEquals(78, rightFirst.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 0, z));
        assertSameSurface(leftFirst, leftSecond);
        assertSameSurface(rightFirst, rightSecond);
    }

    @Test
    void riverBedEntersLakeWithoutClimbingOverTheShore() {
        CellPos lakePos = new CellPos(0, 0);
        Cell river = new Cell(new CellPos(-1, 0), Set.of()).withFeature(Feature.RUN).withCellY(80);
        int z = lakePos.getMiddleBlockZ();
        for (Feature type : List.of(Feature.KETTLE, Feature.ENDORHEIC)) {
            Cell lake = new Cell(lakePos, Set.of()).withFeature(type).withCellY(80).withOverFill(4);
            CellData data = new CellData(lake, 79);
            Watershed watershed = mock(Watershed.class);
            when(watershed.downstream(river.pos())).thenReturn(lakePos);
            when(watershed.upstream(lakePos)).thenReturn(Set.of(river.pos()));
            for (Cell cell : List.of(river, lake)) {
                when(watershed.elevatedCell(cell.pos())).thenReturn(cell);
                when(watershed.flow(cell.pos())).thenReturn(OptionalDouble.of(24));
            }
            int previous = 78;
            for (int chunkX = -1; chunkX <= lakePos.getMiddleBlockX() >> 4; chunkX++) {
                ChunkPos pos = new ChunkPos(chunkX, z >> 4);
                ProtoChunk chunk = chunk(pos);
                List<CarveProposal> cuts = new ArrayList<>(Feature.RUN.handler.carve(river, watershed, pos, 42));
                cuts.addAll(LakeCarver.carve(lakePos, data, Map.of(lakePos, data), pos));
                cuts.addAll(type.handler.carve(lake, watershed, pos, 42));
                for (int x = 0; x < 16; x++) {
                    fillColumn(chunk, x, z & 15, 86);
                }
                ChunkCarving.apply(chunk, pos, cuts);
                for (int x = Math.max(-1, pos.getMinBlockX()); x <= Math.min(lakePos.getMiddleBlockX(), pos.getMaxBlockX()); x++) {
                    int bed = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15);
                    assertTrue(bed <= previous, type + " inlet rose from " + previous + " to " + bed + " at x=" + x);
                    if (x == -1 || x == 0) {
                        assertEquals(78, bed);
                    }
                    previous = bed;
                }
            }
            assertEquals(79 - CommonConfig.get().maxLakeCells(), previous);
            ChunkPos shore = new ChunkPos(0, 0);
            assertTrue(type.handler.carve(lake, watershed, shore, 42).stream()
                .noneMatch(cut -> cut.x() == 0 && cut.z() == 0));
        }
    }

    private void assertSameSurface(ProtoChunk first, ProtoChunk second) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                assertEquals(first.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z),
                    second.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z));
            }
        }
    }

    private ProtoChunk carveRunChunk(ChunkPos pos, Cell west, Cell east, Watershed watershed) {
        ProtoChunk chunk = chunk(pos);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                fillColumn(chunk, x, z, 86);
            }
        }
        List<CarveProposal> proposals = new ArrayList<>(Feature.RUN.handler.carve(west, watershed, pos, 42));
        proposals.addAll(Feature.RUN.handler.carve(east, watershed, pos, 42));
        ChunkCarving.apply(chunk, pos, proposals);
        return chunk;
    }

    private ProtoChunk chunk(ChunkPos pos) {
        Registry<Biome> registry = new MappedRegistry<>(Registries.BIOME, Lifecycle.stable());
        Registry.register(registry, Biomes.PLAINS, mock(Biome.class));
        ProtoChunk chunk = new ProtoChunk(pos, UpgradeData.EMPTY, LevelHeightAccessor.create(-64, 384), registry, null);
        chunk.setStatus(ChunkStatus.BIOMES);
        return chunk;
    }

    private void fillColumn(ProtoChunk chunk, int x, int z, int top) {
        for (int y = 70; y <= top; y++) {
            chunk.setBlockState(new BlockPos(chunk.getPos().getBlockX(x), y, chunk.getPos().getBlockZ(z)),
                Blocks.STONE.defaultBlockState(), false);
        }
    }
}
