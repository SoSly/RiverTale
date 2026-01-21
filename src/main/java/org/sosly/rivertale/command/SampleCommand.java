package org.sosly.rivertale.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.sosly.rivertale.cell.Cell;
import org.sosly.rivertale.cell.CellCache;
import org.sosly.rivertale.cell.CellType;
import org.sosly.rivertale.config.CommonConfig;
import org.sosly.rivertale.core.CellPos;
import org.sosly.rivertale.core.RegionPos;
import org.sosly.rivertale.density.CapturedDensityFunctions;
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.RiverTale;
import net.minecraft.world.level.levelgen.DensityFunction;

public class SampleCommand {

    public static LiteralArgumentBuilder<CommandSourceStack> register() {
        return Commands.literal("sample")
                .then(Commands.literal("all").executes(SampleCommand::all))
                .then(Commands.literal("chunk").executes(SampleCommand::chunk))
                .then(Commands.literal("cell").executes(SampleCommand::cell))
                .then(Commands.literal("region").executes(SampleCommand::region));
    }

    private static int all(CommandContext<CommandSourceStack> context) {
        return execute(context, Level.ALL);
    }

    private static int cell(CommandContext<CommandSourceStack> context) {
        return execute(context, Level.CELL);
    }

    private static int chunk(CommandContext<CommandSourceStack> context) {
        return execute(context, Level.CHUNK);
    }

    private static int region(CommandContext<CommandSourceStack> context) {
        return execute(context, Level.REGION);
    }

    private static int execute(CommandContext<CommandSourceStack> context, Level level) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("This command must be run by a player."));
            return 0;
        }

        try {
            BlockPos pos = player.blockPosition();

            switch (level) {
                case CHUNK -> sendSampleInfo(player, pos);
                case CELL -> sendCellInfo(player, pos);
                case REGION -> sendRegionInfo(player, pos);
                default -> {
                    sendSampleInfo(player, pos);
                    sendCellInfo(player, pos);
                    sendRegionInfo(player, pos);
                }

            }
        } catch (Exception e) {
            RiverTale.LOGGER.error("Error executing sample command", e);
            return 0;
        }

        return 1;
    }

    private static void sendSampleInfo(ServerPlayer player, BlockPos pos) {
        ChunkPos chunk = new ChunkPos(pos);
        Sample sample = SampleCache.get().getOrCompute(pos.getX(), pos.getZ());
        int sampleX = chunk.getMiddleBlockX();
        int sampleZ = chunk.getMiddleBlockZ();
        int y = pos.getY();

        player.sendSystemMessage(withTeleport("Sample " + chunk, sampleX, y, sampleZ));
        player.sendSystemMessage(Component.literal("  ocean: " + sample.isOcean()
            + " (threshold: " + format(CommonConfig.get().oceanThreshold()) + ")"));
        player.sendSystemMessage(Component.literal("  continents: " + format(sample.continents())));
        player.sendSystemMessage(Component.literal("  depth: " + format(sample.depth())));
        player.sendSystemMessage(Component.literal("  estimatedTerrainHeight: " + sample.estimatedTerrainHeight()));
        player.sendSystemMessage(Component.literal("  erosion: " + formatNullable(sample.erosion())));
        player.sendSystemMessage(Component.literal("  ridges: " + formatNullable(sample.ridges())));
        player.sendSystemMessage(Component.literal("  ridgesFolded: " + (sample.ridges() != null ? format(sample.ridgesFolded()) : "null")));
        player.sendSystemMessage(Component.literal("  temperature: " + formatNullable(sample.temperature())));
        player.sendSystemMessage(Component.literal("  vegetation: " + formatNullable(sample.vegetation())));

        DensityFunction riverValleys = CapturedDensityFunctions.get("river_valleys");
        if (riverValleys != null) {
            DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(sampleX, y, sampleZ);
            double riverValleysValue = riverValleys.compute(ctx);
            player.sendSystemMessage(Component.literal("  riverValleys: " + format(riverValleysValue)));
        }
    }

    private static void sendCellInfo(ServerPlayer player, BlockPos pos) {
        CellPos cellPos = new CellPos(pos);
        Cell cell = CellCache.get().getOrCompute(cellPos);
        int y = pos.getY();

        player.sendSystemMessage(withTeleport("=== Cell at " + cellPos + " ===", cellPos.getMiddleBlockX(), y, cellPos.getMiddleBlockZ()));
        player.sendSystemMessage(Component.literal("  Samples: " + cell.samplePositions().size()));
        player.sendSystemMessage(Component.literal("  Average depth: " + format(cell.averageDepth())));
        player.sendSystemMessage(Component.literal("  Average continents: " + format(cell.averageContinents())));
        player.sendSystemMessage(Component.literal("  Feature: " + cell.feature().name()));
        player.sendSystemMessage(Component.literal("  Flow directions: " + cell.flowDirections() + " (sorted by steepness)"));
        player.sendSystemMessage(Component.literal("  entryY: " + formatNullableInt(cell.entryY()) + ", exitY: " + formatNullableInt(cell.exitY())));
        player.sendSystemMessage(Component.literal("  width: " + formatNullableInt(cell.width()) + ", depth: " + formatNullableInt(cell.depth())));
        player.sendSystemMessage(Component.literal("  upstreamCount: " + formatNullableInt(cell.upstreamCount()) + ", downstreamCount: " + formatNullableInt(cell.downstreamCount())));
        player.sendSystemMessage(Component.literal("  terminus: " + (cell.terminus() != null ? cell.terminus().toString() : "(not computed)")));
        player.sendSystemMessage(Component.literal("  isOcean: " + cell.isOcean() + ", isBasin: " + cell.isBasin()));

        try {
            player.sendSystemMessage(Component.literal("  Average erosion: " + format(cell.averageErosion())));
            player.sendSystemMessage(Component.literal("  Average ridges: " + format(cell.averageRidges())));
            player.sendSystemMessage(Component.literal("  Average temperature: " + format(cell.averageTemperature())));
            player.sendSystemMessage(Component.literal("  Average vegetation: " + format(cell.averageVegetation())));
        } catch (IllegalStateException e) {
            player.sendSystemMessage(Component.literal("  (no enriched sample data)"));
        }

        DensityFunction riverValleys = CapturedDensityFunctions.get("river_valleys");
        if (riverValleys != null) {
            DensityFunction.SinglePointContext ctx = new DensityFunction.SinglePointContext(cellPos.getMiddleBlockX(), y, cellPos.getMiddleBlockZ());
            double riverValleysValue = riverValleys.compute(ctx);
            player.sendSystemMessage(Component.literal("  riverValleys: " + format(riverValleysValue)));
        }
    }

    private static void sendRegionInfo(ServerPlayer player, BlockPos pos) {
        RegionPos regionPos = new RegionPos(pos);
        Region region = RegionCache.get().getOrCompute(regionPos);

        Map<CellType, Integer> counts = countCellTypes(regionPos);

        player.sendSystemMessage(Component.literal("Region " + regionPos));
        player.sendSystemMessage(Component.literal("  type: " + region.encode().getString("type")));
        for (CellType type : CellType.values()) {
            int count = counts.getOrDefault(type, 0);
            player.sendSystemMessage(Component.literal("  " + type.name() + ": " + count));
        }
    }

    private static Map<CellType, Integer> countCellTypes(RegionPos regionPos) {
        Map<CellType, Integer> counts = new EnumMap<>(CellType.class);
        CellPos min = regionPos.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();
        CellCache cellCache = CellCache.get();

        for (int x = 0; x < cells; x++) {
            for (int z = 0; z < cells; z++) {
                Cell cell = cellCache.getOrCompute(new CellPos(min.x() + x, min.z() + z));
                CellType type = cell.feature().type;
                counts.merge(type, 1, Integer::sum);
            }
        }

        return counts;
    }

    private static String format(double value) {
        return String.format("%.3f", value);
    }

    private static String formatNullable(Double value) {
        return value != null ? String.format("%.3f", value) : "(not computed)";
    }

    private static String formatNullableInt(Integer value) {
        return value != null ? value.toString() : "(not computed)";
    }

    private static MutableComponent withTeleport(String text, int x, int y, int z) {
        String command = "/tp @s " + x + " " + y + " " + z;
        return Component.literal(text)
            .withStyle(style -> style.withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command)));
    }

    private enum Level {
        ALL,
        CHUNK,
        CELL,
        REGION;
    }
}
