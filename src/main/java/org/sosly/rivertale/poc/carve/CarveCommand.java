package org.sosly.rivertale.poc.carve;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

public class CarveCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("carve")
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
                                                        .executes(CarveCommand::execute)
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

    private static int execute(CommandContext<CommandSourceStack> context) {
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
                "Invalid direction: %s. Use north/south/east/west.", directionStr)));
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
        source.sendSuccess(() -> Component.literal(String.format("Slope: %.4f", executor.getSlope()))
            .withStyle(ChatFormatting.AQUA), false);
        source.sendSuccess(() -> Component.literal(String.format("Blocks removed: %,d", executor.getBlocksRemoved()))
            .withStyle(ChatFormatting.GRAY), false);
        source.sendSuccess(() -> Component.literal(String.format("Blocks placed: %,d", executor.getBlocksPlaced()))
            .withStyle(ChatFormatting.GRAY), false);

        int chunks = executor.getChunksLoaded();
        long elapsed = executor.getElapsedMs();
        double msPerChunk = chunks > 0 ? (double) elapsed / chunks : 0;
        source.sendSuccess(() -> Component.literal(String.format(
            "Elapsed: %,dms across %d chunks (%.1fms/chunk)", elapsed, chunks, msPerChunk))
            .withStyle(ChatFormatting.DARK_GRAY), false);

        return 1;
    }
}
