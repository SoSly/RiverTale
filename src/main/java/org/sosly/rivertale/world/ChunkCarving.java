package org.sosly.rivertale.world;

import java.util.Arrays;
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
        double[] ceilings = null;
        for (CarveProposal proposal : proposals) {
            int x = proposal.x() - pos.getMinBlockX();
            int z = proposal.z() - pos.getMinBlockZ();
            if (x < 0 || x >= 16 || z < 0 || z >= 16 || proposal.influence() <= 0) {
                continue;
            }
            if (proposal.bedCeiling()) {
                if (ceilings == null) {
                    ceilings = new double[256];
                    Arrays.fill(ceilings, Double.POSITIVE_INFINITY);
                }
                int index = x * 16 + z;
                ceilings[index] = Math.min(ceilings[index], proposal.bedY());
                continue;
            }
            double influence = proposal.influence();
            double weight = influence * influence * influence;
            weightedHeights[x][z] += proposal.bedY() * weight;
            weights[x][z] += weight;
        }
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                double bed = weights[x][z] > 0 ? weightedHeights[x][z] / weights[x][z] : Double.POSITIVE_INFINITY;
                if (ceilings != null) {
                    bed = Math.min(bed, ceilings[x * 16 + z]);
                }
                if (Double.isFinite(bed)) {
                    cutColumn(chunk, x, z, (int) Math.round(bed));
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
