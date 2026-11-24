package org.sosly.rivertale.metric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LongSummaryStatistics;

public class Timer {
    private final List<Long> samples = Collections.synchronizedList(new ArrayList<>());

    public void record(long nanos) {
        samples.add(nanos);
    }

    public int count() {
        return samples.size();
    }

    public double avg() {
        return toMicros(summarize(copySamples()).getAverage());
    }

    public double min() {
        LongSummaryStatistics summary = summarize(copySamples());
        return summary.getCount() == 0 ? 0 : toMicros(summary.getMin());
    }

    public double max() {
        LongSummaryStatistics summary = summarize(copySamples());
        return summary.getCount() == 0 ? 0 : toMicros(summary.getMax());
    }

    public double total() {
        return toMicros(summarize(copySamples()).getSum());
    }

    public Snapshot snapshot() {
        List<Long> copy = copySamples();
        if (copy.isEmpty()) {
            return new Snapshot(0, 0, 0, 0, 0);
        }
        LongSummaryStatistics summary = summarize(copy);
        return new Snapshot(copy.size(), toMicros(summary.getAverage()), toMicros(summary.getMin()),
            toMicros(summary.getMax()), percentile(copy, 50));
    }

    private List<Long> copySamples() {
        synchronized (samples) {
            return new ArrayList<>(samples);
        }
    }

    private static LongSummaryStatistics summarize(List<Long> copy) {
        return copy.stream().mapToLong(Long::longValue).summaryStatistics();
    }

    public double median() {
        return percentile(50);
    }

    public double percentile(double p) {
        return percentile(copySamples(), p);
    }

    private static double percentile(List<Long> copy, double p) {
        if (copy.isEmpty()) {
            return 0;
        }
        Collections.sort(copy);
        int idx = (int) Math.ceil((p / 100.0) * copy.size()) - 1;
        return toMicros(copy.get(Math.max(0, idx)));
    }

    public void reset() {
        samples.clear();
    }

    private static double toMicros(double nanos) {
        return nanos / 1_000.0;
    }

    public Record start() {
        return new Record();
    }

    public record Snapshot(int count, double avg, double min, double max, double median) {
    }

    public class Record {
        private final long startTime = System.nanoTime();

        public void stop() {
            record(System.nanoTime() - startTime);
        }
    }
}
