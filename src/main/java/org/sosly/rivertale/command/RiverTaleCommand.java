package org.sosly.rivertale.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.data.ContinentalData;
import org.sosly.rivertale.poc.carve.CardinalDirection;
import org.sosly.rivertale.poc.carve.CarveConfig;
import org.sosly.rivertale.poc.carve.RiverCarveExecutor;
import org.sosly.rivertale.worldgen.cache.ContinentCacheManager;

@Mod.EventBusSubscriber
public class RiverTaleCommand {

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rivertale")
            .then(Commands.literal("c")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerLevel level = source.getLevel();
                    BlockPos pos = BlockPos.containing(source.getPosition());

                    int terrainHeight = level.getChunkSource().getGenerator().getBaseHeight(
                        pos.getX(), pos.getZ(),
                        Heightmap.Types.WORLD_SURFACE_WG,
                        level, level.getChunkSource().randomState()
                    );

                    RandomState randomState = level.getChunkSource().randomState();
                    NoiseRouter router = randomState.router();

                    DensityFunction.SinglePointContext ctxSea =
                        new DensityFunction.SinglePointContext(pos.getX(), 63, pos.getZ());
                    DensityFunction.SinglePointContext ctxTerrain =
                        new DensityFunction.SinglePointContext(pos.getX(), terrainHeight, pos.getZ());

                    double depthAtSea = router.depth().compute(ctxSea);
                    double depthAtTerrain = router.depth().compute(ctxTerrain);
                    double erosion = router.erosion().compute(ctxSea);
                    double continents = router.continents().compute(ctxSea);
                    double ridges = router.ridges().compute(ctxSea);

                    source.sendSuccess(() -> Component.literal("-----").withStyle(ChatFormatting.GRAY), false);
                    source.sendSuccess(() -> Component.literal(String.format("Values at %d, %d:", pos.getX(), pos.getZ()))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Terrain Height: %d", terrainHeight))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Depth at Sea Level: %.3f", depthAtSea))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Depth at Terrain: %.3f", depthAtTerrain))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Erosion: %.3f", erosion))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Continents: %.3f", continents))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Ridges: %.3f", ridges))
                        .withStyle(ChatFormatting.WHITE), false);

                    return 1;
                })
            )
            .then(Commands.literal("evaluate")
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerLevel level = source.getLevel();
                    BlockPos pos = BlockPos.containing(source.getPosition());

                    ContinentCacheManager cacheManager = ContinentCacheManager.forLevel(level);
                    if (cacheManager == null) {
                        source.sendFailure(Component.literal("This command only works in the Overworld!"));
                        return 0;
                    }

                    RandomState randomState = level.getChunkSource().randomState();
                    NoiseRouter router = randomState.router();
                    DensityFunction continentsFunction = router.continents();
                    DensityFunction depthFunction = router.depth();

                    DensityFunction.SinglePointContext ctx =
                        new DensityFunction.SinglePointContext(pos.getX(), 63, pos.getZ());

                    double continentalness = continentsFunction.compute(ctx);

                    source.sendSuccess(() -> Component.literal("Continentalness: ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(String.format("%.3f", continentalness))
                            .withStyle(ChatFormatting.WHITE)), false);

                    source.sendSuccess(() -> Component.literal("Searching for continental center (cached detection)...")
                        .withStyle(ChatFormatting.GRAY), false);

                    ContinentalData continentalData = cacheManager.getContinentalData(pos, continentsFunction, depthFunction);

                    if (continentalData == null) {
                        source.sendSuccess(() -> Component.literal("No continent found (position is in ocean)")
                            .withStyle(ChatFormatting.AQUA), false);
                    } else {
                        source.sendSuccess(() -> Component.literal("Estimated Continental Center: ")
                            .withStyle(ChatFormatting.GOLD)
                            .append(Component.literal(String.format("%d %d ",
                                continentalData.getCenter().getX(), continentalData.getCenter().getZ()))
                                .withStyle(ChatFormatting.WHITE))
                            .append(Component.literal(String.format("(maxDepth: %.3f, high points: %d/%d)",
                                continentalData.getMaxContinentalness(), continentalData.getHighPointCount(),
                                continentalData.getTotalSamples()))
                                .withStyle(ChatFormatting.GRAY)), false);
                    }

                    Component interpretation = getInterpretation(continentalness);
                    source.sendSuccess(() -> Component.literal("Interpretation: ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(interpretation), false);

                    String cacheStats = cacheManager.getCacheStats();
                    source.sendSuccess(() -> Component.literal("Cache: " + cacheStats)
                        .withStyle(ChatFormatting.GRAY), false);

                    return 1;
                })
            )
            .then(Commands.literal("poc")
                .then(Commands.literal("carve")
                    .then(Commands.argument("entryX", IntegerArgumentType.integer())
                        .then(Commands.argument("entryZ", IntegerArgumentType.integer())
                            .then(Commands.literal("at")
                                .then(Commands.argument("entryDist", IntegerArgumentType.integer(0))
                                    .then(Commands.argument("direction", StringArgumentType.word())
                                        .then(Commands.argument("exitX", IntegerArgumentType.integer())
                                            .then(Commands.argument("exitZ", IntegerArgumentType.integer())
                                                .then(Commands.literal("at")
                                                    .then(Commands.argument("exitDist", IntegerArgumentType.integer(0))
                                                        .then(Commands.literal("acc")
                                                            .then(Commands.argument("accumulation", IntegerArgumentType.integer(1))
                                                                .executes(context -> {
                                                                    CommandSourceStack source = context.getSource();
                                                                    ServerLevel level = source.getLevel();

                                                                    int entryX = IntegerArgumentType.getInteger(context, "entryX");
                                                                    int entryZ = IntegerArgumentType.getInteger(context, "entryZ");
                                                                    int entryDist = IntegerArgumentType.getInteger(context, "entryDist");
                                                                    String directionStr = StringArgumentType.getString(context, "direction");
                                                                    int exitX = IntegerArgumentType.getInteger(context, "exitX");
                                                                    int exitZ = IntegerArgumentType.getInteger(context, "exitZ");
                                                                    int exitDist = IntegerArgumentType.getInteger(context, "exitDist");
                                                                    int accumulation = IntegerArgumentType.getInteger(context, "accumulation");

                                                                    CardinalDirection direction = CardinalDirection.fromString(directionStr);
                                                                    if (direction == null) {
                                                                        source.sendFailure(Component.literal(String.format(
                                                                            "Invalid direction: %s. Use north/south/east/west.",
                                                                            directionStr)));
                                                                        return 0;
                                                                    }

                                                                    int entryY = CarveConfig.SEA_LEVEL + entryDist * CarveConfig.ELEVATION_PER_CELL;
                                                                    int exitY = CarveConfig.SEA_LEVEL + exitDist * CarveConfig.ELEVATION_PER_CELL;
                                                                    source.sendSuccess(() -> Component.literal(String.format(
                                                                        "Starting carve: (%d, %d) Y=%d → (%d, %d) Y=%d, direction=%s, acc=%d",
                                                                        entryX, entryZ, entryY, exitX, exitZ, exitY, direction, accumulation))
                                                                        .withStyle(ChatFormatting.YELLOW), false);

                                                                    RiverCarveExecutor executor = new RiverCarveExecutor(
                                                                        level, entryX, entryZ, entryDist, direction, exitX, exitZ, exitDist, accumulation
                                                                    );

                                                                    source.sendSuccess(() -> Component.literal(String.format(
                                                                        "Calculated width=%d, depth=%d, slope=%.4f",
                                                                        executor.getWidth(), executor.getChannelDepth(), executor.getSlope()))
                                                                        .withStyle(ChatFormatting.AQUA), false);

                                                                    executor.execute();

                                                                    source.sendSuccess(() -> Component.literal("River carved successfully!")
                                                                        .withStyle(ChatFormatting.GREEN), false);
                                                                    source.sendSuccess(() -> Component.literal(String.format("Width: %d blocks, Depth: %d blocks",
                                                                        executor.getWidth(), executor.getChannelDepth()))
                                                                        .withStyle(ChatFormatting.YELLOW), false);
                                                                    source.sendSuccess(() -> Component.literal(String.format("Slope: %.4f",
                                                                        executor.getSlope()))
                                                                        .withStyle(ChatFormatting.AQUA), false);
                                                                    source.sendSuccess(() -> Component.literal(String.format("Blocks removed: %,d",
                                                                        executor.getBlocksRemoved()))
                                                                        .withStyle(ChatFormatting.GRAY), false);
                                                                    source.sendSuccess(() -> Component.literal(String.format("Blocks placed: %,d",
                                                                        executor.getBlocksPlaced()))
                                                                        .withStyle(ChatFormatting.GRAY), false);

                                                                    int chunks = executor.getChunksLoaded();
                                                                    long elapsed = executor.getElapsedMs();
                                                                    double msPerChunk = chunks > 0 ? (double) elapsed / chunks : 0;
                                                                    source.sendSuccess(() -> Component.literal(String.format(
                                                                        "Elapsed: %,dms across %d chunks (%.1fms/chunk)",
                                                                        elapsed, chunks, msPerChunk))
                                                                        .withStyle(ChatFormatting.DARK_GRAY), false);

                                                                    return 1;
                                                                })
                                                            )
                                                        )
                                                    )
                                                )
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                )
            )
        );
    }

    private static Component getInterpretation(double continentalness) {
        if (continentalness < -0.5) {
            return Component.literal("Deep Ocean").withStyle(ChatFormatting.BLUE);
        }
        if (continentalness < 0.0) {
            return Component.literal("Coastal Waters").withStyle(ChatFormatting.DARK_AQUA);
        }
        if (continentalness < 0.3) {
            return Component.literal("Coastal Land").withStyle(ChatFormatting.YELLOW);
        }
        if (continentalness < 0.7) {
            return Component.literal("Inland").withStyle(ChatFormatting.DARK_GREEN);
        }
        return Component.literal("Continental Center").withStyle(ChatFormatting.GREEN);
    }
}
