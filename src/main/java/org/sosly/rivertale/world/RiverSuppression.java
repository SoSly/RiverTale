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
    private static final Map<String, ClampParams> CLAMP_CONFIG = new HashMap<>();
    private static final Set<String> loggedKeys = Collections.synchronizedSet(new HashSet<>());
    private static final Set<String> loggedClamps = Collections.synchronizedSet(new HashSet<>());
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

    public record ClampParams(double min, double max) {}

    public static void init() {
        if (initialized) {
            return;
        }

        // Vanilla Minecraft
        register("overworld/ridges_folded", -0.799, 1.0);

        // Lithosphere
        register("river_valleys", -0.5, 1.0);

        initialized = true;
        RiverTale.LOGGER.info("RiverSuppression initialized with {} density function overrides", CLAMP_CONFIG.size());
    }

    public static void shutdown() {
        CLAMP_CONFIG.clear();
        loggedKeys.clear();
        loggedClamps.clear();
        initialized = false;
    }

    private static void register(String path, double min, double max) {
        CLAMP_CONFIG.put(path, new ClampParams(min, max));
        RiverTale.LOGGER.debug("Registered river suppression for {}: clamp({}, {})", path, min, max);
    }

    public static DensityFunction intercept(DensityFunctions.HolderHolder holder, DensityFunction.Visitor visitor) {
        Optional<ResourceKey<DensityFunction>> key = holder.function().unwrapKey();
        if (key.isEmpty()) {
            return null;
        }

        String path = key.get().location().getPath();
        if (loggedKeys.add(path)) {
            RiverTale.LOGGER.info("HolderHolder.mapAll sees key: {}", path);
        }

        DensityFunction wired = visitor.apply(
            new DensityFunctions.HolderHolder(
                new Holder.Direct<>(holder.function().value().mapAll(visitor))
            )
        );

        CapturedDensityFunctions.put(path, wired);

        ClampParams clampParams = CLAMP_CONFIG.get(path);
        if (clampParams == null) {
            return null;
        }

        if (loggedClamps.add(path)) {
            RiverTale.LOGGER.info("Clamping {} to ({}, {}) to suppress river carving", path, clampParams.min(), clampParams.max());
        }

        return wired.clamp(clampParams.min(), clampParams.max());
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
