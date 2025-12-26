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
import org.sosly.rivertale.density.Sample;
import org.sosly.rivertale.density.SampleCache;
import org.sosly.rivertale.region.Region;
import org.sosly.rivertale.region.RegionCache;
import org.sosly.rivertale.RiverTale;

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
        player.sendSystemMessage(Component.literal("  erosion: " + format(sample.erosion())));
        player.sendSystemMessage(Component.literal("  ridges: " + format(sample.ridges())));
        player.sendSystemMessage(Component.literal("  temperature: " + format(sample.temperature())));
        player.sendSystemMessage(Component.literal("  vegetation: " + format(sample.vegetation())));
    }

    private static void sendCellInfo(ServerPlayer player, BlockPos pos) {
        CellPos cellPos = new CellPos(pos);
        Cell cell = CellCache.get().getOrCompute(cellPos);
        Sample sample = cell.sample();
        int y = pos.getY();

        player.sendSystemMessage(withTeleport("Cell " + cellPos, cellPos.getMiddleBlockX(), y, cellPos.getMiddleBlockZ()));
        player.sendSystemMessage(Component.literal("  feature: " + cell.feature().name()));
        player.sendSystemMessage(Component.literal("  type: " + cell.feature().type.name()));
        player.sendSystemMessage(Component.literal("  flow: " + cell.flowDirection().name()));
        player.sendSystemMessage(Component.literal("  continents: " + format(sample.continents())));
        player.sendSystemMessage(Component.literal("  depth: " + format(sample.depth())));
        player.sendSystemMessage(Component.literal("  erosion: " + format(sample.erosion())));
        player.sendSystemMessage(Component.literal("  ridges: " + format(sample.ridges())));
        player.sendSystemMessage(Component.literal("  temperature: " + format(sample.temperature())));
        player.sendSystemMessage(Component.literal("  vegetation: " + format(sample.vegetation())));
    }

    private static void sendRegionInfo(ServerPlayer player, BlockPos pos) {
        CellCache cellCache = CellCache.get();
        SampleCache sampleCache = SampleCache.get();
        RegionPos regionPos = new RegionPos(pos);
        Region region = RegionCache.get().getOrCompute(regionPos, cellCache, sampleCache);

        Map<CellType, Integer> counts = countCellTypes(regionPos, cellCache);

        player.sendSystemMessage(Component.literal("Region " + regionPos));
        player.sendSystemMessage(Component.literal("  type: " + region.encode().getString("type")));
        for (CellType type : CellType.values()) {
            int count = counts.getOrDefault(type, 0);
            player.sendSystemMessage(Component.literal("  " + type.name() + ": " + count));
        }
    }

    private static Map<CellType, Integer> countCellTypes(RegionPos regionPos, CellCache cellCache) {
        Map<CellType, Integer> counts = new EnumMap<>(CellType.class);
        CellPos min = regionPos.getMinCell();
        int cells = CommonConfig.get().cellsPerRegion();

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
