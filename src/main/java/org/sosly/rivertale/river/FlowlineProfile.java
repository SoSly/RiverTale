package org.sosly.rivertale.river;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

final class FlowlineProfile {
    private FlowlineProfile() {
    }

    static List<Long> sampledFalls(int[] heights, int outlet) {
        List<Long> falls = new ArrayList<>();
        for (int i = 1; i < heights.length; i++) {
            long downstream = i == heights.length - 1 ? outlet : heights[i];
            falls.add(Math.max(0, (long) heights[i - 1] - downstream));
        }
        return List.copyOf(falls);
    }

    static Optional<List<Long>> carvedFalls(int[] heights, int outlet, int minimumFall) {
        if (heights.length < 2) {
            return Optional.of(List.of());
        }
        List<Long> falls = new ArrayList<>();
        long upstream = heights[0];
        for (int i = 1; i < heights.length; i++) {
            long downstream = Math.min(heights[i], upstream - minimumFall);
            if (i == heights.length - 1) {
                downstream = outlet;
            }
            long fall = upstream - downstream;
            if (fall < minimumFall) {
                return Optional.empty();
            }
            falls.add(fall);
            upstream = downstream;
        }
        return Optional.of(List.copyOf(falls));
    }
}
