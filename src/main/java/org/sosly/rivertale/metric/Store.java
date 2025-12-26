package org.sosly.rivertale.metric;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Store {
    private static volatile Store instance;

    private final Map<String, Timer> timers;
    private final Map<String, Ratio> ratios;

    private Store() {
        timers = new ConcurrentHashMap<>();
        ratios = new ConcurrentHashMap<>();
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
        getInstance().timers.clear();
        getInstance().ratios.clear();
    }

    public static Timer getTimer(Class<?> clazz, String name) {
        String metricName = clazz.getName() + "_" + name;
        return getInstance().timers.computeIfAbsent(metricName, k -> new Timer());
    }

    public static Ratio getRatio(Class<?> clazz, String name) {
        String metricName = clazz.getName() + "_" + name;
        return getInstance().ratios.computeIfAbsent(metricName, k -> new Ratio());
    }

    public static Map<String, Timer> timers() {
        return Collections.unmodifiableMap(getInstance().timers);
    }

    public static Map<String, Ratio> ratios() {
        return Collections.unmodifiableMap(getInstance().ratios);
    }
}
