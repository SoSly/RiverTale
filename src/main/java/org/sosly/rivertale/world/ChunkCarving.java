package org.sosly.rivertale.world;

import java.util.List;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import org.sosly.rivertale.terrain.CarveProposal;

final class ChunkCarving {
    private ChunkCarving() {}

    static void apply(ChunkAccess chunk, ChunkPos pos, List<CarveProposal> proposals) {
        double[][] weightedHeights = new double[16][16];
        double[][] weights = new double[16][16];
        for (CarveProposal proposal : proposals) {
            int x = proposal.x() - pos.getMinBlockX();
            int z = proposal.z() - pos.getMinBlockZ();
            if (x < 0 || x >= 16 || z < 0 || z >= 16 || proposal.influence() <= 0) {
                continue;
            }
            double influence = proposal.influence();
            double weight = influence * influence * influence;
            weightedHeights[x][z] += proposal.bedY() * weight;
            weights[x][z] += weight;
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (weights[x][z] > 0) {
                    int bedY = (int) Math.round(weightedHeights[x][z] / weights[x][z]);
                    cutColumn(chunk, x, z, bedY);
                }
            }
        }
    }

    private static void cutColumn(ChunkAccess chunk, int x, int z, int bedY) {
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
        int startY = Math.max(bedY + 1, chunk.getMinBuildHeight());
        if (surfaceY < startY) {
            return;
        }
        Heightmap surface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
        Heightmap floor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int y = startY; y <= surfaceY; y++) {
            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
            if (section.getBlockState(x, y & 15, z).isAir()) {
                continue;
            }
            // Noise generation already owns the section locks.
            section.setBlockState(x, y & 15, z, air, false);
            surface.update(x, y, z, air);
            floor.update(x, y, z, air);
        }
    }
}
