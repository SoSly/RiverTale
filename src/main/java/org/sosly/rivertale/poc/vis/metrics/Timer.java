package org.sosly.rivertale.poc.vis.metrics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Timer {
    private final List<Long> samples = new ArrayList<>();

    public void record(long nanos) {
        samples.add(nanos);
    }

    public int count() {
        return samples.size();
    }

    public double avg() {
        if (samples.isEmpty()) return 0;
        long sum = 0;
        for (long sample : samples) sum += sample;
        return toMicros(sum / (double) samples.size());
    }

    public double min() {
        if (samples.isEmpty()) return 0;
        long min = Long.MAX_VALUE;
        for (long sample : samples) min = Math.min(min, sample);
        return toMicros(min);
    }

    public double max() {
        if (samples.isEmpty()) return 0;
        long max = Long.MIN_VALUE;
        for (long sample : samples) max = Math.max(max, sample);
        return toMicros(max);
    }

    public double total() {
        if (samples.isEmpty()) return 0;
        long sum = 0;
        for (long sample : samples) sum += sample;
        return toMicros(sum);
    }

    public double median() {
        return percentile(50);
    }

    public double percentile(double p) {
        if (samples.isEmpty()) return 0;
        List<Long> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int idx = (int) Math.ceil((p / 100.0) * sorted.size()) - 1;
        return toMicros(sorted.get(Math.max(0, idx)));
    }

    public void reset() {
        samples.clear();
    }

    private double toMicros(double nanos) {
        return nanos / 1_000.0;
    }

    public Record start() {
        return new Record();
    }

    public class Record {
        private final long startTime = System.nanoTime();

        public void stop() {
            record(System.nanoTime() - startTime);
        }
    }
}
