package org.sosly.rivertale.density;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.levelgen.DensityFunction;

public class CapturedDensityFunctions {
    private static final Map<String, DensityFunction> CAPTURED = new ConcurrentHashMap<>();

    public static void put(String path, DensityFunction function) {
        CAPTURED.put(path, function);
    }

    public static DensityFunction get(String path) {
        return CAPTURED.get(path);
    }

    public static void clear() {
        CAPTURED.clear();
    }
}
