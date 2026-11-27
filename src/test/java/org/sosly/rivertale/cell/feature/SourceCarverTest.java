package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.river.RiverSpline;
import org.sosly.rivertale.river.Watershed;
import org.sosly.rivertale.terrain.CarveProposal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SourceCarverTest {
    @Test
    void evenZeroFlowSourcesCarveBothRillsAndConnectAtThePreviewedCrossing() {
        Cell cell = new Cell(new CellPos(0, 0), Set.of()).withCellY(80);
        Cell exit = new Cell(new CellPos(1, 0), Set.of()).withCellY(76);
        Watershed watershed = mock(Watershed.class);
        when(watershed.downstream(cell.pos())).thenReturn(exit.pos());
        when(watershed.elevatedCell(exit.pos())).thenReturn(exit);
        when(watershed.flow(cell.pos())).thenReturn(OptionalDouble.of(0));
        for (Feature feature : List.of(Feature.SPRING, Feature.SEEP, Feature.SNOWMELT, Feature.CRATER)) {
            FeatureHandler handler = feature.handler;
            List<RiverSpline> paths = handler.buildSplines(cell, List.of(), Optional.of(exit), 42);
            for (RiverSpline path : paths) {
                Vec3 point = path.pointAt(0.25);
                int x = (int) Math.floor(point.x);
                int z = (int) Math.floor(point.z);
                int cuts = 0;
                for (int offset = -1; offset <= 1; offset++) {
                    int row = z + offset;
                    List<CarveProposal> proposals = handler.carve(cell, watershed, new ChunkPos(x >> 4, row >> 4), 42);
                    if (proposals.stream().anyMatch(p -> p.x() == x && p.z() == row && Math.round(p.bedY()) < 80)) {
                        cuts++;
                    }
                }
                assertTrue(cuts >= 2, feature.name());
            }
            Vec3 end = paths.get(paths.size() - 1).pointAt(1);
            int x = (int) end.x - 1;
            for (int z = (int) end.z - 1; z <= (int) end.z; z++) {
                int row = z;
                List<CarveProposal> proposals = handler.carve(cell, watershed, new ChunkPos(x >> 4, row >> 4), 42);
                assertTrue(proposals.stream().anyMatch(p -> p.x() == x && p.z() == row && Math.round(p.bedY()) == 75));
            }
        }
    }

    @Test
    void craterBowlScalesWithTheCellAndMeetsTheOverflowAtItsDownstreamEdge() {
        for (int size : List.of(32, 96)) {
            double length = 7.0 * size / 12;
            double width = 5.0 * size / 6;
            double lipX = 0.6875 * size;
            double midX = lipX - length / 2;
            double midZ = size / 2.0;
            List<RiverSpline> paths = List.of(t -> new Vec3(lipX - (1 - t) * size / 16, 80, midZ),
                t -> new Vec3(lipX + (size - lipX) * t, 80, midZ));
            int x = (int) Math.floor(midX);
            int z = (int) midZ;
            assertEquals(80 - size / 6, bedAt(paths, x, z, size));
            assertEquals(79, bedAt(paths, x, (int) Math.ceil(midZ + width / 2 - 0.5) - 1, size));
            assertEquals(80, bedAt(paths, x, (int) Math.ceil(midZ + width / 2 - 0.5), size));
            assertEquals(80, bedAt(paths, (int) Math.ceil(lipX - length - 0.5) - 1, z, size));
            for (; x < size; x++) {
                assertTrue(bedAt(paths, x, z, size) < 80, "Uncut sill at " + x);
            }
        }
    }

    private long bedAt(List<RiverSpline> paths, int x, int z, int size) {
        return new Crater().carveHeadwater(paths, 0, new ChunkPos(x >> 4, z >> 4), size).stream()
            .filter(p -> p.x() == x && p.z() == z).mapToLong(p -> Math.round(p.bedY())).min().orElse(80);
    }
}
