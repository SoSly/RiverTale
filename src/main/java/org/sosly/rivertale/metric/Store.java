package org.sosly.rivertale.metric;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.sosly.rivertale.config.CommonConfig;

public class Store {
    private static volatile Store instance;
    private static final Timer NOOP_TIMER = new Timer() {
        @Override
        public void record(long nanos) {
        }
    };
    private static final Ratio NOOP_RATIO = new Ratio() {
        @Override
        public void success() {
        }

        @Override
        public void failure() {
        }
    };

    private static final Distribution NOOP_DISTRIBUTION = new Distribution() {
        @Override
        public void record(double value) {
        }
    };

    private final Map<String, Distribution> distributions;
    private final Map<String, Timer> timers;
    private final Map<String, Ratio> ratios;

    private Store() {
        distributions = new ConcurrentHashMap<>();
        timers = new ConcurrentHashMap<>();
        ratios = new ConcurrentHashMap<>();
    }

    private static boolean isEnabled() {
        return CommonConfig.get().enableMetrics();
    }

    public static Store getInstance() {
        if (instance == null) {
            synchronized (Store.class) {
                if (instance == null) {
                    instance = new Store();
                }
            }
        }
        return instance;
    }

    public static void clear() {
        getInstance().distributions.clear();
        getInstance().timers.clear();
        getInstance().ratios.clear();
    }

    public static Timer getTimer(Class<?> clazz, String name) {
        if (!isEnabled()) {
            return NOOP_TIMER;
        }
        String metricName = clazz.getName() + "_" + name;
        return getInstance().timers.computeIfAbsent(metricName, k -> new Timer());
    }

    public static Ratio getRatio(Class<?> clazz, String name) {
        if (!isEnabled()) {
            return NOOP_RATIO;
        }
        String metricName = clazz.getName() + "_" + name;
        return getInstance().ratios.computeIfAbsent(metricName, k -> new Ratio());
    }

    public static Distribution getDistribution(Class<?> clazz, String name) {
        if (!isEnabled()) {
            return NOOP_DISTRIBUTION;
        }
        String metricName = clazz.getName() + "_" + name;
        return getInstance().distributions.computeIfAbsent(metricName, k -> new Distribution());
    }

    public static Map<String, Distribution> distributions() {
        return Collections.unmodifiableMap(getInstance().distributions);
    }

    public static Map<String, Timer> timers() {
        return Collections.unmodifiableMap(getInstance().timers);
    }

    public static Map<String, Ratio> ratios() {
        return Collections.unmodifiableMap(getInstance().ratios);
    }
}
