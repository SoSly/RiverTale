package org.sosly.rivertale.poc.vis.metrics;

public class Ratio {
    private long successes = 0;
    private long attempts = 0;

    public void success() {
        successes++;
        attempts++;
    }

    public void failure() {
        attempts++;
    }

    public long successes() {
        return successes;
    }

    public long attempts() {
        return attempts;
    }

    public double rate() {
        if (attempts == 0) return 0;
        return successes / (double) attempts;
    }

    public void reset() {
        successes = 0;
        attempts = 0;
    }
}
