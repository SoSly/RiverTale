package org.sosly.rivertale.client;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.feature.Feature;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.region.RegionType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatershedPreviewTest {
    @Test
    void waterfallPreviewDrawsTheLipAndFootAsDistinctPointsOnABend() {
        CellPos source = new CellPos(0, 1);
        CellPos falls = new CellPos(1, 1);
        CellPos run = new CellPos(2, 0);
        List<ClientRegionCache.Edge> edges = List.of(new ClientRegionCache.Edge(source, falls, 128, 96),
            new ClientRegionCache.Edge(falls, run, 96, 54));
        ClientRegionCache.Cell feature = new ClientRegionCache.Cell(falls, Feature.WATERFALL, 96, OptionalInt.empty(), OptionalDouble.empty());
        ClientRegionCache.Region region = new ClientRegionCache.Region(new RegionPos(0, 0), RegionType.FLUVIAL,
            List.of(), List.of(feature), edges, List.of(), List.of());
        List<List<Vec3>> paths = WatershedPreview.build(List.of(region), 42).paths();
        List<Vec3> face = paths.stream().filter(path -> path.get(0).y == 95 && path.get(path.size() - 1).y == 54)
            .findFirst().orElseThrow();
        Vec3 lip = face.get(0);
        Vec3 foot = face.get(face.size() - 1);
        assertEquals(2, Math.hypot(foot.x - lip.x, foot.z - lip.z), 1e-9);
        double size = CommonConfig.get().cellBlocks();
        assertTrue(Math.abs(lip.x - falls.getMiddleBlockX()) < size / 4);
        Vec3 inlet = new Vec3(falls.getMinBlockX(), 96, falls.getMiddleBlockZ());
        Vec3 approach = inlet.add(1, 0, 0);
        Vec3 exit = new Vec3(falls.getMaxBlockX(), 54, falls.getMinBlockZ());
        assertTrue(paths.stream().anyMatch(path -> path.get(0).equals(inlet) && path.get(path.size() - 1).equals(approach)));
        assertTrue(paths.stream().anyMatch(path -> path.get(0).equals(approach) && path.get(path.size() - 1).equals(lip)));
        assertTrue(paths.stream().anyMatch(path -> path.get(0).equals(foot) && path.get(path.size() - 1).equals(exit)
            && path.stream().allMatch(point -> point.y == 54)));
    }

    @Test
    void runIsDrawnOnceBetweenTwoFallbackCellsAcrossARegionEdge() {
        int boundary = CommonConfig.get().cellsPerRegion();
        CellPos source = new CellPos(boundary - 1, 0);
        CellPos run = new CellPos(boundary, 0);
        CellPos outlet = new CellPos(boundary + 1, 0);
        List<ClientRegionCache.Edge> edges = List.of(new ClientRegionCache.Edge(source, run, 100, 54),
            new ClientRegionCache.Edge(run, outlet, 54, 53));
        ClientRegionCache.Region west = new ClientRegionCache.Region(new RegionPos(0, 0), RegionType.FLUVIAL,
            List.of(), List.of(), edges, List.of(), List.of());
        ClientRegionCache.Cell feature = new ClientRegionCache.Cell(run, Feature.RUN, 54, OptionalInt.empty(), OptionalDouble.empty());
        ClientRegionCache.Region east = new ClientRegionCache.Region(new RegionPos(1, 0), RegionType.FLUVIAL,
            List.of(), List.of(feature), edges, List.of(), List.of());
        WatershedPreview preview = WatershedPreview.build(List.of(west, east), 42);
        assertEquals(3, preview.paths().size());
        Vec3 entry = new Vec3(run.getMinBlockX(), 54, run.getMiddleBlockZ());
        Vec3 exit = new Vec3(run.getMaxBlockX(), 53, run.getMiddleBlockZ());
        List<Vec3> bend = preview.paths().stream().filter(path -> path.get(0).equals(entry)).findFirst().orElseThrow();
        assertEquals(exit, bend.get(bend.size() - 1));
        assertTrue(bend.stream().anyMatch(point -> point.z != entry.z));
        assertTrue(preview.paths().stream().anyMatch(path -> path.get(path.size() - 1).equals(entry)));
        assertTrue(preview.paths().stream().anyMatch(path -> path.get(0).equals(exit)));
    }
}
