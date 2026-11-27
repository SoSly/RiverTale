package org.sosly.rivertale.cell.feature;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import net.minecraft.core.BlockPos;
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

class WaterfallCarverTest {
    private final Cell upstream = cell(-1, 100);
    private final Cell falls = cell(0, 100);
    private final Cell downstream = cell(1, 80);
    private final FeatureHandler handler = Feature.WATERFALL.handler;
    private final Watershed watershed = mock(Watershed.class);

    @Test
    void bedDropsAtThePreviewedFaceAndLevelsOutAtTheFoot() {
        when(watershed.upstream(falls.pos())).thenReturn(Set.of(upstream.pos()));
        when(watershed.downstream(falls.pos())).thenReturn(downstream.pos());
        when(watershed.elevatedCell(upstream.pos())).thenReturn(upstream);
        when(watershed.elevatedCell(downstream.pos())).thenReturn(downstream);
        when(watershed.flow(upstream.pos())).thenReturn(OptionalDouble.of(18));
        when(watershed.flow(falls.pos())).thenReturn(OptionalDouble.of(18));

        List<RiverSpline> paths = handler.buildSplines(falls, List.of(upstream), Optional.of(downstream), 42);
        RiverSpline face = paths.get(2);
        assertEquals(98, bedAt(paths.get(0).pointAt(0)));
        assertEquals(97, bedAt(face.pointAt(0).add(-1, 0, 0)));
        long faceBed = bedAt(face.pointAt(0.5));
        assertTrue(faceBed < 97 && faceBed > 78);
        assertEquals(78, bedAt(face.pointAt(1).add(1, 0, 0)));
        assertEquals(78, bedAt(paths.get(3).pointAt(1)));
    }

    private long bedAt(Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        List<CarveProposal> cuts = handler.carve(falls, watershed, new ChunkPos(pos), 42).stream()
            .filter(p -> p.x() == pos.getX() && p.z() == pos.getZ()).toList();
        assertEquals(1, cuts.size());
        return Math.round(cuts.get(0).bedY());
    }

    private static Cell cell(int x, int y) {
        return new Cell(new CellPos(x, 0), Set.of()).withCellY(y);
    }
}
