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
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.sosly.rivertale.config.RiverConfig;
import org.sosly.rivertale.poc.carve.CardinalDirection;
import org.sosly.rivertale.poc.carve.CarveConfig;
import org.sosly.rivertale.poc.carve.RiverCarveExecutor;
import org.sosly.rivertale.poc.fill.RiverFillExecutor;

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

                    int terrainHeight = level.getHeight(CarveConfig.TERRAIN_HEIGHTMAP, pos.getX(), pos.getZ());
                    String biomeName = level.getBiome(pos).unwrapKey()
                        .map(key -> key.location().toString())
                        .orElse("unknown");

                    RandomState randomState = level.getChunkSource().randomState();
                    NoiseRouter router = randomState.router();

                    DensityFunction.SinglePointContext ctxTerrain =
                        new DensityFunction.SinglePointContext(pos.getX(), terrainHeight, pos.getZ());
                    DensityFunction.SinglePointContext ctxSea =
                        new DensityFunction.SinglePointContext(pos.getX(), 63, pos.getZ());

                    double continentsSea = router.continents().compute(ctxSea);
                    double continentsTerrain = router.continents().compute(ctxTerrain);
                    double depthSea = router.depth().compute(ctxSea);
                    double depthTerrain = router.depth().compute(ctxTerrain);
                    double erosionSea = router.erosion().compute(ctxSea);
                    double erosionTerrain = router.erosion().compute(ctxTerrain);
                    double ridgesSea = router.ridges().compute(ctxSea);
                    double ridgesTerrain = router.ridges().compute(ctxTerrain);

                    source.sendSuccess(() -> Component.literal("-----").withStyle(ChatFormatting.GRAY), false);
                    source.sendSuccess(() -> Component.literal(String.format("Noise at (%d, %d), terrain height %d:", pos.getX(), pos.getZ(), terrainHeight))
                        .withStyle(ChatFormatting.YELLOW), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Biome: %s", biomeName))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Continents: %.3f at y=63, %.3f at y=%d", continentsSea, continentsTerrain, terrainHeight))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Depth: %.3f at y=63, %.3f at y=%d", depthSea, depthTerrain, terrainHeight))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Erosion: %.3f at y=63, %.3f at y=%d", erosionSea, erosionTerrain, terrainHeight))
                        .withStyle(ChatFormatting.WHITE), false);
                    source.sendSuccess(() -> Component.literal(String.format("  Ridges: %.3f at y=63, %.3f at y=%d", ridgesSea, ridgesTerrain, terrainHeight))
                        .withStyle(ChatFormatting.WHITE), false);

                    return 1;
                })
            )
            .then(CellCommand.register())
            .then(LocateCommand.register())
            .then(VisualizeCommand.register())
            .then(Commands.literal("poc")
                .then(Commands.literal("d")
                    .then(Commands.argument("x", IntegerArgumentType.integer())
                        .then(Commands.argument("z", IntegerArgumentType.integer())
                            .executes(context -> {
                                CommandSourceStack source = context.getSource();
                                ServerLevel level = source.getLevel();

                                int x = IntegerArgumentType.getInteger(context, "x");
                                int z = IntegerArgumentType.getInteger(context, "z");

                                int chunkX = x >> 4;
                                int chunkZ = z >> 4;

                                RandomState randomState = level.getChunkSource().randomState();
                                NoiseRouter router = randomState.router();

                                DensityFunction.SinglePointContext ctx =
                                    new DensityFunction.SinglePointContext(x, 63, z);

                                double continentsBefore = router.continents().compute(ctx);
                                double depthBefore = router.depth().compute(ctx);
                                double depthWeight = RiverConfig.DEPTH_WEIGHT.get();
                                double densityBefore = continentsBefore + (depthBefore * depthWeight);

                                boolean wasLoaded = level.hasChunk(chunkX, chunkZ);

                                source.sendSuccess(() -> Component.literal(String.format(
                                    "BEFORE chunk load at (%d, %d) [chunk %d, %d]:",
                                    x, z, chunkX, chunkZ))
                                    .withStyle(ChatFormatting.YELLOW), false);
                                source.sendSuccess(() -> Component.literal(String.format(
                                    "  Continents: %.6f, Depth: %.6f, Combined: %.6f",
                                    continentsBefore, depthBefore, densityBefore))
                                    .withStyle(ChatFormatting.WHITE), false);
                                source.sendSuccess(() -> Component.literal(String.format(
                                    "  Chunk was %s",
                                    wasLoaded ? "already loaded" : "NOT loaded"))
                                    .withStyle(wasLoaded ? ChatFormatting.GREEN : ChatFormatting.RED), false);

                                level.getChunk(chunkX, chunkZ);

                                double continentsAfter = router.continents().compute(ctx);
                                double depthAfter = router.depth().compute(ctx);
                                double densityAfter = continentsAfter + (depthAfter * depthWeight);

                                source.sendSuccess(() -> Component.literal("AFTER chunk load:")
                                    .withStyle(ChatFormatting.YELLOW), false);
                                source.sendSuccess(() -> Component.literal(String.format(
                                    "  Continents: %.6f, Depth: %.6f, Combined: %.6f",
                                    continentsAfter, depthAfter, densityAfter))
                                    .withStyle(ChatFormatting.WHITE), false);

                                boolean match = densityBefore == densityAfter;

                                if (match) {
                                    source.sendSuccess(() -> Component.literal("RESULT: Density is DETERMINISTIC")
                                        .withStyle(ChatFormatting.GREEN), false);
                                } else {
                                    source.sendFailure(Component.literal(String.format(
                                        "RESULT: Density CHANGED! Diff: %.6f",
                                        densityAfter - densityBefore)));
                                }

                                return 1;
                            })
                        )
                    )
                )
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
                .then(Commands.literal("fill")
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
                                                                        "Starting fill: (%d, %d) Y=%d → (%d, %d) Y=%d, direction=%s, acc=%d",
                                                                        entryX, entryZ, entryY, exitX, exitZ, exitY, direction, accumulation))
                                                                        .withStyle(ChatFormatting.YELLOW), false);

                                                                    RiverFillExecutor executor = new RiverFillExecutor(
                                                                        level, entryX, entryZ, entryDist, direction, exitX, exitZ, exitDist, accumulation
                                                                    );

                                                                    source.sendSuccess(() -> Component.literal(String.format(
                                                                        "Calculated width=%d, depth=%d",
                                                                        executor.getWidth(), executor.getChannelDepth()))
                                                                        .withStyle(ChatFormatting.AQUA), false);

                                                                    executor.execute();

                                                                    source.sendSuccess(() -> Component.literal("River filled successfully!")
                                                                        .withStyle(ChatFormatting.GREEN), false);
                                                                    source.sendSuccess(() -> Component.literal(String.format("Water blocks placed: %,d",
                                                                        executor.getWaterBlocksPlaced()))
                                                                        .withStyle(ChatFormatting.GRAY), false);
                                                                    source.sendSuccess(() -> Component.literal(String.format("Path points processed: %,d",
                                                                        executor.getPathPointsProcessed()))
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
}
