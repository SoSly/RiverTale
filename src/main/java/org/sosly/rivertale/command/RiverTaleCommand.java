package org.sosly.rivertale.command;

import com.mojang.brigadier.CommandDispatcher;
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
import org.sosly.rivertale.data.ContinentalData;
import org.sosly.rivertale.worldgen.analysis.ContinentalnessCalculator;
import org.sosly.rivertale.worldgen.cache.ContinentCacheManager;

@Mod.EventBusSubscriber
public class RiverTaleCommand {

    @SubscribeEvent
    public static void onCommandRegister(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("rivertale")
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
                    DensityFunction depthFunction = router.depth();
                    DensityFunction erosionFunction = router.erosion();

                    DensityFunction.SinglePointContext ctx =
                        new DensityFunction.SinglePointContext(pos.getX(), 63, pos.getZ());

                    double depth = depthFunction.compute(ctx);
                    double erosion = erosionFunction.compute(ctx);
                    final double continentalness = ContinentalnessCalculator.calculateContinentalness(depth, erosion);
                    final double finalDepth = depth;
                    final double finalErosion = erosion;

                    source.sendSuccess(() -> Component.literal("Continentalness: ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(String.format("%.3f ", continentalness))
                            .withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(String.format("(depth: %.3f, erosion: %.3f)", finalDepth, finalErosion))
                            .withStyle(ChatFormatting.GRAY)), false);

                    source.sendSuccess(() -> Component.literal("Searching for continental center (cached detection)...")
                        .withStyle(ChatFormatting.GRAY), false);

                    ContinentalData continentalData = cacheManager.getContinentalData(pos, depthFunction, erosionFunction);

                    source.sendSuccess(() -> Component.literal("Estimated Continental Center: ")
                        .withStyle(ChatFormatting.GOLD)
                        .append(Component.literal(String.format("%d %d ",
                            continentalData.getCenter().getX(), continentalData.getCenter().getZ()))
                            .withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(String.format("(max: %.3f, high points: %d/%d)",
                            continentalData.getMaxContinentalness(), continentalData.getHighPointCount(),
                            continentalData.getTotalSamples()))
                            .withStyle(ChatFormatting.GRAY)), false);

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
