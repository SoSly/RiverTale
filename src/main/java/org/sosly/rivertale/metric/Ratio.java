package org.sosly.rivertale.metric;

import java.util.concurrent.atomic.AtomicLong;

public class Ratio {
    private final AtomicLong successes = new AtomicLong(0);
    private final AtomicLong attempts = new AtomicLong(0);

    public void success() {
        successes.incrementAndGet();
        attempts.incrementAndGet();
    }

    public void failure() {
        attempts.incrementAndGet();
    }

    public long successes() {
        return successes.get();
    }

    public long attempts() {
        return attempts.get();
    }

    public double rate() {
        long a = attempts.get();
        if (a == 0) {
            return 0;
        }
        return successes.get() / (double) a;
    }

    public void reset() {
        successes.set(0);
        attempts.set(0);
    }
}
