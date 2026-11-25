package org.sosly.rivertale.config;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import java.util.List;
import net.minecraftforge.common.ForgeConfigSpec;

record TuningConfigValues(ForgeConfigSpec spec, UnmodifiableConfig data) {
    int get(ForgeConfigSpec.IntValue value) {
        Object raw = read(value);
        if (raw instanceof Integer integer) {
            return integer;
        }
        if (raw instanceof Long integer) {
            return Math.toIntExact(integer);
        }
        throw invalid(value.getPath());
    }

    double get(ForgeConfigSpec.DoubleValue value) {
        return ((Number) read(value)).doubleValue();
    }

    boolean get(ForgeConfigSpec.BooleanValue value) {
        return (Boolean) read(value);
    }

    private Object read(ForgeConfigSpec.ConfigValue<?> value) {
        List<String> path = value.getPath();
        Object raw = data.getOrElse(path, value.getDefault());
        ForgeConfigSpec.ValueSpec setting = spec.getSpec().get(path);
        if (!setting.test(raw)) {
            throw invalid(path);
        }
        return raw;
    }

    private static IllegalArgumentException invalid(List<String> path) {
        return new IllegalArgumentException("Invalid value for " + String.join(".", path));
    }
}
