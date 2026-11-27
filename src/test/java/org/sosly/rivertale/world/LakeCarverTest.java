package org.sosly.rivertale.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionType;
import org.sosly.rivertale.river.PreparedRiverData.CellData;
import org.sosly.rivertale.river.RiverRegion;
import org.sosly.rivertale.terrain.CarveProposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LakeCarverTest {
    @Test
    void bothLakeTypesCutBelowAverageEvenAtZeroFill() {
        CellPos pos = new CellPos(-1, 0);
        int extra = CommonConfig.get().maxLakeCells();
        for (Feature type : List.of(Feature.KETTLE, Feature.ENDORHEIC)) {
            for (int fill : List.of(0, 5)) {
                CellData member = member(pos, type, 74 - fill, fill);
                int x = pos.getMiddleBlockX();
                int z = pos.getMiddleBlockZ();
                Map<CellPos, CellData> cells = Map.of(pos, member);
                double center = bedAt(pos, cells, x, z);
                assertEquals(74 - fill - extra, Math.round(center));
                assertTrue(bedAt(pos, cells, pos.getMinBlockX(), z) > center);
            }
        }
    }

    @Test
    void offPathPocketSharesTheBedAcrossAChunkAndRegionBoundary() {
        CellPos west = new CellPos(-1, 0);
        CellPos east = new CellPos(0, 0);
        Map<CellPos, CellData> cells = new HashMap<>();
        cells.put(west, member(west, Feature.KETTLE, 69, 5));
        cells.put(east, member(east, Feature.KETTLE, 73, 1));
        int z = west.getMiddleBlockZ();
        double left = bedAt(west, cells, -1, z);
        double right = bedAt(east, cells, 0, z);
        assertTrue(Math.abs(left - right) < 1);
        assertTrue(right < 69);
    }

    private static double bedAt(CellPos owner, Map<CellPos, CellData> cells, int x, int z) {
        RegionPos region = owner.getRegion();
        RiverRegion snapshot = new RiverRegion(new Region(region, RegionType.INLAND), cells, List.of(), List.of());
        ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
        List<CarveProposal> cuts = RiverShaping.carveNearby(chunk, 0, 1, p -> snapshot, f -> f.handler);
        assertTrue(cuts.stream().allMatch(cut -> cut.x() >> 4 == chunk.x && cut.z() >> 4 == chunk.z));
        return cuts.stream().filter(cut -> cut.x() == x && cut.z() == z).findFirst().orElseThrow().bedY();
    }

    private static CellData member(CellPos pos, Feature type, int average, int fill) {
        return new CellData(new Cell(pos, Set.of()).withFeature(type).withOverFill(fill), average);
    }
}
