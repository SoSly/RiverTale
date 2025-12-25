package org.sosly.rivertale.poc.vis.metrics;

import java.util.HashMap;
import java.util.Map;

public class Store {
    private static Store instance;

    private final Map<String, Timer> timers;
    private final Map<String, Ratio> ratios;

    private Store() {
        Store.instance = this;

        timers = new HashMap<>();
        ratios = new HashMap<>();
    }

    public static Store getInstance() {
        if (Store.instance != null) {
            return Store.instance;
        }

        return new Store();
    }

    public static void clear() {
        getInstance().timers.clear();
        getInstance().ratios.clear();
    }

    public static Timer getTimer(Class<?> clazz, String name) {
        String metricName = clazz.getName() + "_" + name;
        Timer timer = getInstance().timers.get(metricName);
        if (timer == null) {
            timer = new Timer();
            getInstance().timers.put(metricName, timer);
        }

        return timer;
    }

    public static Ratio getRatio(Class<?> clazz, String name) {
        String metricName = clazz.getName() + "_" + name;
        Ratio ratio = getInstance().ratios.get(metricName);
        if (ratio == null) {
            ratio = new Ratio();
            getInstance().ratios.put(metricName, ratio);
        }

        return ratio;
    }
}
