package org.sosly.rivertale.cell.feature;

import net.minecraft.world.level.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.Direction;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureTest {

    @BeforeEach
    void setUp() {
        SampleCache.init(chunk -> new Sample(chunk, 0.5, 0.0, 0, 0, 0, 0));
    }

    @AfterEach
    void tearDown() {
        SampleCache.shutdown();
    }

    @Test
    void classifiesHighScoreAsSnowmelt() {
        CellPos pos = new CellPos(10, 20);
        Sample sample = new Sample(new ChunkPos(0, 0), 0.7, 0.7, 0, 0, 0, 0);
        Cell original = new Cell(pos, sample, Feature.DEFAULT, Direction.SOUTH);

        Cell cell = Feature.classify(original);

        assertEquals(Feature.SNOWMELT, cell.feature());
        assertEquals(pos, cell.pos());
        assertEquals(sample, cell.sample());
    }

    @Test
    void classifiesLowScoreAsDefault() {
        CellPos pos = new CellPos(10, 20);
        Sample sample = new Sample(new ChunkPos(0, 0), 0.1, 0.1, 0, 0, 0, 0);
        Cell original = new Cell(pos, sample, Feature.DEFAULT, Direction.SOUTH);

        Cell cell = Feature.classify(original);

        assertEquals(Feature.DEFAULT, cell.feature());
    }

    @Test
    void classifiesFromExistingCell() {
        CellPos pos = new CellPos(5, 5);
        Sample sample = new Sample(new ChunkPos(0, 0), 0.8, 0.8, 0, 0, 0, 0);
        Cell original = new Cell(pos, sample, Feature.DEFAULT, Direction.SOUTH);

        Cell classified = Feature.classify(original);

        assertEquals(Feature.SNOWMELT, classified.feature());
        assertEquals(pos, classified.pos());
        assertEquals(sample, classified.sample());
    }
}
