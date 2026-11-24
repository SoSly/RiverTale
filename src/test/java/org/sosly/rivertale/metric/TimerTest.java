package org.sosly.rivertale.metric;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerTest {
    @Test
    void snapshotReportsOnlyTheSamplesRecordedWhenItWasTaken() {
        Timer timer = new Timer();
        assertEquals(new Timer.Snapshot(0, 0, 0, 0, 0), timer.snapshot());
        timer.record(3_000);
        timer.record(1_000);

        Timer.Snapshot first = timer.snapshot();
        timer.record(2_000);

        assertEquals(new Timer.Snapshot(2, 2, 1, 3, 1), first);
        assertEquals(new Timer.Snapshot(3, 2, 1, 3, 2), timer.snapshot());
    }

    @Test
    void averagingRemainsCorrectWhileAnotherThreadRecordsTimings() throws InterruptedException {
        Timer timer = new Timer();
        for (int i = 0; i < 50_000; i++) {
            timer.record(1_000);
        }
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean recording = new AtomicBoolean(true);
        Thread recorder = new Thread(() -> {
            while (recording.get()) {
                timer.record(1_000);
                started.countDown();
                LockSupport.parkNanos(10_000);
            }
        });
        recorder.setDaemon(true);
        recorder.start();
        try {
            assertTrue(started.await(5, TimeUnit.SECONDS));
            for (int i = 0; i < 40; i++) {
                assertEquals(1, timer.avg());
                Timer.Snapshot snapshot = timer.snapshot();
                assertEquals(new Timer.Snapshot(snapshot.count(), 1, 1, 1, 1), snapshot);
            }
        } finally {
            recording.set(false);
            recorder.join(5_000);
        }
        assertFalse(recorder.isAlive());
    }
}
