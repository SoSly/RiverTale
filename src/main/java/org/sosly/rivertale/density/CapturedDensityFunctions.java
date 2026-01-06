package org.sosly.rivertale.density;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.levelgen.DensityFunction;

public class CapturedDensityFunctions {
    private static final Map<String, DensityFunction> captured = new ConcurrentHashMap<>();

    public static void put(String path, DensityFunction function) {
        captured.put(path, function);
    }

    public static DensityFunction get(String path) {
        return captured.get(path);
    }

    public static void clear() {
        captured.clear();
    }
}
