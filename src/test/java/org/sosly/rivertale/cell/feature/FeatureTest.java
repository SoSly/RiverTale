package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.density.Sample;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureTest {

    @Test
    void classifiesHighScoreAsSnowmelt() {
        CellPos pos = new CellPos(10, 20);
        Sample sample = new Sample(new ChunkPos(0, 0), 0.7, 0.7, 0, 0, 0, 0);
        Cell original = new Cell(pos, sample, Feature.DEFAULT);

        Cell cell = Feature.classify(original);

        assertEquals(Feature.SNOWMELT, cell.feature());
        assertEquals(pos, cell.pos());
        assertEquals(sample, cell.sample());
    }

    @Test
    void classifiesLowScoreAsDefault() {
        CellPos pos = new CellPos(10, 20);
        Sample sample = new Sample(new ChunkPos(0, 0), 0.1, 0.1, 0, 0, 0, 0);
        Cell original = new Cell(pos, sample, Feature.DEFAULT);

        Cell cell = Feature.classify(original);

        assertEquals(Feature.DEFAULT, cell.feature());
    }

    @Test
    void classifiesFromExistingCell() {
        CellPos pos = new CellPos(5, 5);
        Sample sample = new Sample(new ChunkPos(0, 0), 0.8, 0.8, 0, 0, 0, 0);
        Cell original = new Cell(pos, sample, Feature.DEFAULT);

        Cell classified = Feature.classify(original);

        assertEquals(Feature.SNOWMELT, classified.feature());
        assertEquals(pos, classified.pos());
        assertEquals(sample, classified.sample());
    }
}
