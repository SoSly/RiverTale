package org.sosly.rivertale.metric;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Distribution {
    private final List<Double> samples = new ArrayList<>();

    public synchronized void record(double value) {
        samples.add(value);
    }

    public synchronized Snapshot snapshot() {
        if (samples.isEmpty()) {
            return new Snapshot(0, 0, 0, 0, 0);
        }
        List<Double> sorted = new ArrayList<>(samples);
        Collections.sort(sorted);
        int count = sorted.size();
        double sum = 0;
        for (double value : sorted) {
            sum += value;
        }
        int middle = count / 2;
        double median = count % 2 == 0 ? (sorted.get(middle - 1) + sorted.get(middle)) / 2 : sorted.get(middle);
        return new Snapshot(count, sum / count, sorted.get(0), sorted.get(count - 1), median);
    }

    public record Snapshot(int count, double avg, double min, double max, double median) {
    }
}
