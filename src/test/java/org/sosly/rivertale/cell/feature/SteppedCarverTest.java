package org.sosly.rivertale.cell.feature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SteppedCarverTest {
    @Test
    void rapidsCarveEachShelfAtItsOwnElevation() {
        checkShelves(Feature.RAPIDS, 3);
    }

    @Test
    void cascadesCarveEachShelfAtItsOwnElevation() {
        checkShelves(Feature.CASCADE, 8);
    }

    private void checkShelves(Feature feature, int drop) {
        Cell upstream = new Cell(new CellPos(-1, 0), Set.of()).withCellY(81);
        Cell course = new Cell(new CellPos(0, 0), Set.of()).withFeature(feature).withCellY(80);
        Cell downstream = new Cell(new CellPos(1, 0), Set.of()).withCellY(80 - drop);
        Watershed watershed = mock(Watershed.class);
        when(watershed.upstream(course.pos())).thenReturn(Set.of(upstream.pos()));
        when(watershed.elevatedCell(upstream.pos())).thenReturn(upstream);
        when(watershed.downstream(course.pos())).thenReturn(downstream.pos());
        when(watershed.elevatedCell(downstream.pos())).thenReturn(downstream);
        when(watershed.flow(upstream.pos())).thenReturn(OptionalDouble.of(18));
        when(watershed.flow(course.pos())).thenReturn(OptionalDouble.of(18));
        FeatureHandler handler = feature.handler;
        List<RiverSpline> reaches = handler.buildSplines(course, List.of(upstream), Optional.of(downstream), 42);
        Map<ChunkPos, List<CarveProposal>> chunks = new HashMap<>();
        for (RiverSpline reach : reaches) {
            if (reach.pointAt(0).y != reach.pointAt(1).y) {
                continue;
            }
            Vec3 middle = reach.pointAt(0.5);
            int x = (int) Math.floor(middle.x);
            int z = (int) Math.floor(middle.z);
            ChunkPos chunk = new ChunkPos(x >> 4, z >> 4);
            List<CarveProposal> proposals = chunks.computeIfAbsent(chunk, pos -> handler.carve(course, watershed, pos, 42));
            CarveProposal center = null;
            for (CarveProposal proposal : proposals) {
                if (proposal.x() == x && proposal.z() == z) {
                    assertNull(center, "A column must not be counted twice at a shelf join");
                    center = proposal;
                }
            }
            assertNotNull(center);
            assertEquals((long) middle.y - 2, Math.round(center.bedY()));
        }
    }

    @Test
    void splittingAChannelDoesNotRestartItsFlowAtTheJoin() {
        RiverSpline whole = t -> new Vec3(16 * t, 80, 8.5);
        List<RiverSpline> split = List.of(t -> whole.pointAt(t / 4), t -> whole.pointAt(0.25 + 0.75 * t));
        ChunkPos chunk = new ChunkPos(0, 0);
        List<CarveProposal> expected = CourseCarver.carve(List.of(whole), 1, 40, chunk, 16);
        List<CarveProposal> actual = CourseCarver.carve(split, 1, 40, chunk, 16);
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertEquals(expected.get(i).x(), actual.get(i).x());
            assertEquals(expected.get(i).z(), actual.get(i).z());
            assertEquals(expected.get(i).bedY(), actual.get(i).bedY(), 1e-9);
        }
    }
}
