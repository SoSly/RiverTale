package org.sosly.rivertale.world;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.river.Watershed;

public class RiverBuilder {
    public static void generateRiverMap(ChunkAccess chunk) {
        RegionPos center = new RegionPos(chunk.getPos().getMiddleBlockPosition(0));
        SampleCache sampleCache = SampleCache.get();
        RegionType.classify(center, sampleCache);

        for (Direction dir : Direction.D8) {
            RegionType.classify(center.relative(dir), sampleCache);
        }
    }

    public static void carveFeature(ChunkAccess chunk) {
        BlockPos blockPos = chunk.getPos().getMiddleBlockPosition(0);
        CellPos cellPos = new CellPos(blockPos);
        RegionPos regionPos = new RegionPos(cellPos);

        Cell cell = CellCache.get().getOrCompute(cellPos);
        Region region = RegionCache.get().getOrCompute(regionPos, CellCache.get(), SampleCache.get());

        Feature.carve(cell, region.watershed(), chunk);
    }
}
