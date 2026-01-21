package org.sosly.rivertale.world;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.density.CapturedDensityFunctions;

public class RiverSuppression {
    private static final Map<String, Interceptor> INTERCEPTOR_CONFIG = new HashMap<>();
    private static final Set<String> LOGGED_KEYS = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> LOGGED_INTERCEPTS = Collections.synchronizedSet(new HashSet<>());
    private static boolean initialized = false;
    private static boolean loggedBiomeFilter = false;

    private static final ResourceKey<Biome> RIVER = ResourceKey.create(
        Registries.BIOME,
        new ResourceLocation("minecraft", "river")
    );
    private static final ResourceKey<Biome> FROZEN_RIVER = ResourceKey.create(
        Registries.BIOME,
        new ResourceLocation("minecraft", "frozen_river")
    );

    public interface Interceptor {
        DensityFunction apply(DensityFunction f);
    }
    public record Abs() implements Interceptor {
        public DensityFunction apply(DensityFunction f) {
            return f.abs();
        }
    }
    public record Clamp(double min, double max) implements Interceptor {
        public DensityFunction apply(DensityFunction f) {
            return f.clamp(min, max);
        }
    }

    public static void init() {
        if (initialized) {
            return;
        }

        // Vanilla Minecraft
        register("overworld/ridges_folded", new Abs());

        // Lithosphere
        register("river_valleys", new Clamp(-0.5, 1.0));

        initialized = true;
        RiverTale.LOGGER.info("RiverSuppression initialized with {} density function overrides", INTERCEPTOR_CONFIG.size());
    }

    public static void shutdown() {
        INTERCEPTOR_CONFIG.clear();
        LOGGED_KEYS.clear();
        LOGGED_INTERCEPTS.clear();
        initialized = false;
    }

    private static void register(String path, Interceptor interceptor) {
        INTERCEPTOR_CONFIG.put(path, interceptor);
        RiverTale.LOGGER.debug("Registered river suppression for {}", path);
    }

    public static DensityFunction intercept(DensityFunctions.HolderHolder holder, DensityFunction.Visitor visitor) {
        Optional<ResourceKey<DensityFunction>> key = holder.function().unwrapKey();
        if (key.isEmpty()) {
            return null;
        }

        String path = key.get().location().getPath();
        DensityFunction wired = visitor.apply(
            new DensityFunctions.HolderHolder(
                new Holder.Direct<>(holder.function().value().mapAll(visitor))
            )
        );

        CapturedDensityFunctions.put(path, wired);

        Interceptor interceptor = INTERCEPTOR_CONFIG.get(path);
        if (interceptor == null) {
            return null;
        }

        if (LOGGED_INTERCEPTS.add(path)) {
            RiverTale.LOGGER.info("Intercepting {} to suppress river carving", path);
        }

        return interceptor.apply(wired);
    }

    public static Climate.ParameterList<Holder<Biome>> filterRiverBiomes(Climate.ParameterList<Holder<Biome>> original) {
        if (!loggedBiomeFilter) {
            RiverTale.LOGGER.info("Filtering river biomes from world generation");
            loggedBiomeFilter = true;
        }

        return new Climate.ParameterList<>(
            original.values().stream()
                .filter(pair -> {
                    Holder<Biome> biome = pair.getSecond();
                    Optional<ResourceKey<Biome>> key = biome.unwrapKey();
                    if (key.isEmpty()) {
                        return true;
                    }
                    return !key.get().equals(RIVER) && !key.get().equals(FROZEN_RIVER);
                })
                .collect(Collectors.toList())
        );
    }
}
