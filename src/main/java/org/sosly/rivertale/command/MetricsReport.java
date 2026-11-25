package org.sosly.rivertale.command;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import org.sosly.rivertale.metric.Distribution;
import org.sosly.rivertale.metric.Ratio;
import org.sosly.rivertale.metric.Store;
import org.sosly.rivertale.metric.Timer;

final class MetricsReport {
    private static final String NO_METRICS = "No metrics recorded yet.";
    private static final String TOPICS = "More: /rivertale metrics <pipeline|rivers|cache>";
    private static final Pattern CAMEL_CASE = Pattern.compile("([a-z])([A-Z])");

    private static final NamedMetric NETWORK_BUILD = new NamedMetric("RiverNetwork_build", "Whole network build");
    private static final List<Stage> STAGES = List.of(
        new Stage(new NamedMetric("RiverPreparation_prepare", "Prepare river data"), "RiverPreparation", List.of(
            new NamedMetric("RiverGraph_routing", "River routing"),
            new NamedMetric("RegionType_classify", "Region classification"),
            new NamedMetric("RegionType_isCoastal", "Coastal check"),
            new NamedMetric("Coastal_boundaries", "Coastal boundaries"),
            new NamedMetric("Fluvial_boundaries", "Fluvial boundaries"),
            new NamedMetric("Feature_classify", "Cell feature classification"),
            new NamedMetric("RtfSampleProvider_sampleCore", "Terrain sampling")
        )),
        new Stage(new NamedMetric("FlowlinePreparation_prepare", "Prepare flowlines"), "FlowlinePreparation", List.of(
            new NamedMetric("SourceTrace_trace", "Trace region sources"),
            new NamedMetric("FlowlineTracer_trace", "Trace individual flowlines")
        )),
        new Stage(new NamedMetric("WatershedPreparation_gather", "Gather watershed inputs"), "WatershedPreparation", List.of()),
        new Stage(new NamedMetric("WatershedBuilder_build", "Build watersheds"), "", List.of())
    );
    private static final List<NamedMetric> RIVER_RECORDS = List.of(
        new NamedMetric("FlowlineTracer_lengthBlocks", "Longest traced path: %,.1f blocks"),
        new NamedMetric("FlowlineTracer_netFallBlocks", "Greatest source-to-sea drop: %,.0f blocks"),
        new NamedMetric("WatershedBuilder_riverCells", "Largest watershed: %,.0f river cells")
    );
    private static final List<NamedMetric> CACHES = List.of(
        new NamedMetric("RegionTypeCache_hits", "Region type"),
        new NamedMetric("RegionCache_hits", "Region"),
        new NamedMetric("SampleCache_hits", "Samples"),
        new NamedMetric("CellCache_hits", "Cells")
    );

    private MetricsReport() {
    }

    static List<String> overview() {
        Map<String, Timer> timers = shortNames(Store.timers());
        Timer.Snapshot build = timerSnapshot(timers, NETWORK_BUILD);
        List<String> timings = new ArrayList<>();
        addSummaryTimer(timings, NETWORK_BUILD.label(), build);
        for (Stage stage : STAGES) {
            addSummaryTimer(timings, "  " + stage.total().label(), timerSnapshot(timers, stage.total()));
        }
        List<String> records = riverRecords();
        List<String> cacheRates = cacheRates(cacheStats());
        if (timings.isEmpty() && records.isEmpty() && cacheRates.isEmpty()) {
            return List.of(NO_METRICS, TOPICS);
        }

        List<String> lines = new ArrayList<>();
        lines.add(build.count() == 0 ? "RiverTale Metrics" : format("RiverTale Metrics · %,d network build%s",
            build.count(), build.count() == 1 ? "" : "s"));
        addSection(lines, format("%-25s %7s %7s %7s", "Pipeline (ms)", "Avg", "Min", "Max"), timings);
        addSection(lines, "River records", records);
        addSection(lines, "Cache hit rates", cacheRates);
        lines.add(TOPICS);
        return lines;
    }

    static List<String> pipeline() {
        Map<String, Timer> timers = shortNames(Store.timers());
        Map<String, Distribution> distributions = shortNames(Store.distributions());
        List<String> lines = new ArrayList<>();
        addDetailTimer(lines, NETWORK_BUILD.label(), timerSnapshot(timers, NETWORK_BUILD));
        for (Stage stage : STAGES) {
            List<String> details = new ArrayList<>();
            addDetailTimer(details, "Stage total", timerSnapshot(timers, stage.total()));
            for (NamedMetric metric : stage.subTimers()) {
                addDetailTimer(details, metric.label(), timerSnapshot(timers, metric));
            }
            addDistributions(details, distributions, stage.workloadOwner());
            addSection(lines, stage.total().label(), details);
        }
        return topic("Pipeline diagnostics", lines);
    }

    static List<String> rivers() {
        Map<String, Distribution> distributions = shortNames(Store.distributions());
        Map<String, Ratio> ratios = shortNames(Store.ratios());
        List<String> trace = new ArrayList<>();
        addDetailRatio(trace, "Valid traces", ratios.get("FlowlineTracer_success"));
        addDetailRatio(trace, "Flat carve feasible", ratios.get("FlowlineTracer_carveFlatFeasible"));
        addDetailRatio(trace, "Descending carve feasible", ratios.get("FlowlineTracer_carveDescendingFeasible"));
        addDistributions(trace, distributions, "FlowlineTracer");
        List<String> watershed = new ArrayList<>();
        addDistributions(watershed, distributions, "WatershedBuilder");
        List<String> lines = new ArrayList<>();
        addSection(lines, "Flowlines", trace);
        addSection(lines, "Watersheds", watershed);
        return topic("River diagnostics", lines);
    }

    static List<String> cache() {
        List<String> lines = new ArrayList<>();
        for (CacheStat cache : cacheStats()) {
            RatioCounts counts = cache.counts();
            lines.add(format("  %s: %,d / %,d hits (%.1f%%) · %,d missed", cache.label(), counts.successes(),
                counts.attempts(), counts.percent(), counts.attempts() - counts.successes()));
        }
        return topic("Cache hit rates", lines);
    }

    private static List<String> riverRecords() {
        Map<String, Distribution> distributions = shortNames(Store.distributions());
        List<String> lines = new ArrayList<>();
        for (NamedMetric metric : RIVER_RECORDS) {
            Distribution distribution = distributions.get(metric.key());
            if (distribution == null) {
                continue;
            }
            Distribution.Snapshot snapshot = distribution.snapshot();
            if (snapshot.count() > 0) {
                lines.add("  " + format(metric.label(), snapshot.max()));
            }
        }
        return lines;
    }

    private static List<CacheStat> cacheStats() {
        Map<String, Ratio> ratios = shortNames(Store.ratios());
        List<CacheStat> stats = new ArrayList<>();
        for (NamedMetric metric : CACHES) {
            Ratio ratio = ratios.get(metric.key());
            if (ratio == null) {
                continue;
            }
            RatioCounts counts = ratioCounts(ratio);
            if (counts.attempts() > 0) {
                stats.add(new CacheStat(metric.label(), counts));
            }
        }
        stats.sort(Comparator.comparingDouble(cache -> cache.counts().percent()));
        return stats;
    }

    private static List<String> cacheRates(List<CacheStat> stats) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < stats.size(); i += 2) {
            String line = "  " + cacheRate(stats.get(i));
            if (i + 1 < stats.size()) {
                line += " · " + cacheRate(stats.get(i + 1));
            }
            lines.add(line);
        }
        return lines;
    }

    private static String cacheRate(CacheStat cache) {
        return format("%s %.1f%%", cache.label(), cache.counts().percent());
    }

    private static Timer.Snapshot timerSnapshot(Map<String, Timer> timers, NamedMetric metric) {
        Timer timer = timers.get(metric.key());
        return timer == null ? new Timer.Snapshot(0, 0, 0, 0, 0) : timer.snapshot();
    }

    private static void addSummaryTimer(List<String> lines, String label, Timer.Snapshot timing) {
        if (timing.count() > 0) {
            lines.add(format("%-25s %7.1f %7.1f %7.1f", label, timing.avg() / 1_000, timing.min() / 1_000, timing.max() / 1_000));
        }
    }

    private static void addDetailTimer(List<String> lines, String label, Timer.Snapshot timing) {
        if (timing.count() > 0) {
            lines.add(format("  %s · %,d call%s", label, timing.count(), timing.count() == 1 ? "" : "s"));
            lines.add(format("    Avg %s · Min %s · Max %s · Median %s", duration(timing.avg()), duration(timing.min()),
                duration(timing.max()), duration(timing.median())));
        }
    }

    private static String duration(double micros) {
        if (micros < 1_000) {
            return format("%,.1f µs", micros);
        }
        return format("%,.2f ms", micros / 1_000);
    }

    private static void addDistributions(List<String> lines, Map<String, Distribution> distributions, String owner) {
        String prefix = owner + "_";
        for (Map.Entry<String, Distribution> entry : distributions.entrySet()) {
            if (!entry.getKey().startsWith(prefix)) {
                continue;
            }
            Distribution.Snapshot stats = entry.getValue().snapshot();
            if (stats.count() == 0) {
                continue;
            }
            String label = metricLabel(entry.getKey().substring(prefix.length()));
            lines.add(format("  %s · %,d sample%s", label, stats.count(), stats.count() == 1 ? "" : "s"));
            lines.add(format("    Avg %,.1f · Min %,.1f · Max %,.1f · Median %,.1f", stats.avg(), stats.min(), stats.max(), stats.median()));
        }
    }

    private static String metricLabel(String name) {
        String words = CAMEL_CASE.matcher(name).replaceAll("$1 $2").toLowerCase(Locale.ROOT);
        return Character.toUpperCase(words.charAt(0)) + words.substring(1);
    }

    private static void addDetailRatio(List<String> lines, String label, Ratio ratio) {
        if (ratio == null) {
            return;
        }
        RatioCounts counts = ratioCounts(ratio);
        if (counts.attempts() > 0) {
            lines.add(format("  %s: %,d / %,d (%.1f%%)", label, counts.successes(), counts.attempts(), counts.percent()));
        }
    }

    private static RatioCounts ratioCounts(Ratio ratio) {
        long successes = ratio.successes();
        long attempts = ratio.attempts();
        return new RatioCounts(Math.min(successes, attempts), attempts);
    }

    private static <T> Map<String, T> shortNames(Map<String, T> metrics) {
        Map<String, T> sorted = new TreeMap<>();
        metrics.forEach((key, value) -> sorted.put(key.substring(key.lastIndexOf('.') + 1), value));
        return sorted;
    }

    private static void addSection(List<String> lines, String heading, List<String> section) {
        if (!section.isEmpty()) {
            lines.add(heading);
            lines.addAll(section);
        }
    }

    private static List<String> topic(String heading, List<String> details) {
        List<String> lines = new ArrayList<>();
        lines.add("RiverTale Metrics · " + heading);
        lines.addAll(details.isEmpty() ? List.of(NO_METRICS) : details);
        return lines;
    }

    private static String format(String template, Object... values) {
        return String.format(Locale.ROOT, template, values);
    }

    private record NamedMetric(String key, String label) {
    }

    private record Stage(NamedMetric total, String workloadOwner, List<NamedMetric> subTimers) {
    }

    private record CacheStat(String label, RatioCounts counts) {
    }

    private record RatioCounts(long successes, long attempts) {
        double percent() {
            return 100.0 * successes / attempts;
        }
    }
}
