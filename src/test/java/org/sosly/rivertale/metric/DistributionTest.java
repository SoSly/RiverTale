package org.sosly.rivertale.metric;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DistributionTest {
    @Test
    void summarizesUnsortedSamplesWithAnEvenMedian() {
        Distribution distribution = new Distribution();
        distribution.record(50);
        distribution.record(10);
        distribution.record(100);
        distribution.record(20);

        assertEquals(new Distribution.Snapshot(4, 45, 10, 100, 35), distribution.snapshot());
    }

    @Test
    void summarizesAnOddMedianAndKeepsSnapshotsStable() {
        Distribution distribution = new Distribution();
        distribution.record(50);
        Distribution.Snapshot first = distribution.snapshot();
        distribution.record(10);
        distribution.record(120);

        assertEquals(new Distribution.Snapshot(1, 50, 50, 50, 50), first);
        assertEquals(new Distribution.Snapshot(3, 60, 10, 120, 50), distribution.snapshot());
    }

    @Test
    void emptyDistributionHasNoSamples() {
        assertEquals(new Distribution.Snapshot(0, 0, 0, 0, 0), new Distribution().snapshot());
    }
}
