package org.sosly.rivertale.cell.feature;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.FlowDirection;
import org.sosly.rivertale.density.Sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureTest {

    @Test
    void classifyReturnsNoneWhenNoHandlerMatches() {
        CellPos pos = new CellPos(10, 20);
        Sample sample = new Sample(new ChunkPos(0, 0), 0.5, 0.5, 0.0, 0.0, 0.0, 0.0);
        Map<ChunkPos, Sample> samples = new HashMap<>();
        samples.put(sample.pos(), sample);
        int y = sample.estimatedTerrainHeight();
        Cell original = new Cell(pos, samples, List.of(FlowDirection.SOUTH), Feature.NONE, null, y, y, null, null, null, null, null, null, null);

        Cell cell = Feature.classify(original, null);

        assertEquals(Feature.NONE, cell.feature());
        assertEquals(pos, cell.pos());
        assertEquals(samples, cell.samples());
    }

    @Test
    void classifyPreservesExistingFeature() {
        CellPos pos = new CellPos(5, 5);
        Sample sample = new Sample(new ChunkPos(0, 0), 0.5, 0.5, 0.0, 0.0, 0.0, 0.0);
        Map<ChunkPos, Sample> samples = new HashMap<>();
        samples.put(sample.pos(), sample);
        int y = sample.estimatedTerrainHeight();
        Cell original = new Cell(pos, samples, List.of(FlowDirection.SOUTH), Feature.RUN, null, y, y, null, null, null, null, null, null, null);

        Cell classified = Feature.classify(original, null);

        assertEquals(Feature.NONE, classified.feature());
    }
}
