package org.sosly.rivertale.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.sosly.rivertale.RiverTale;
import org.sosly.rivertale.worldgen.RiverNoiseGenerator;

@Mod.EventBusSubscriber(modid = RiverTale.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class DebugRenderer {
    private static final int RENDER_RADIUS = 128;
    private static final int SAMPLE_SPACING = 4;
    private static final int BEAM_HEIGHT = 8;

    private static boolean debugMode = false;
    private static RiverNoiseGenerator generator;

    public static boolean toggleDebugMode() {
        debugMode = !debugMode;
        if (debugMode && generator == null) {
            long seed = getWorldSeed();
            generator = new RiverNoiseGenerator(seed);
        }
        return debugMode;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (!debugMode || generator == null) {
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        if (generator == null) {
            long seed = getWorldSeed();
            generator = new RiverNoiseGenerator(seed);
        }

        renderRiverPotential(event.getPoseStack(), event.getCamera().getPosition(), minecraft.renderBuffers().bufferSource());
    }

    private static long getWorldSeed() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getSingleplayerServer() != null) {
            return minecraft.getSingleplayerServer().getWorldData().worldGenOptions().seed();
        }
        return 0L;
    }

    private static void renderRiverPotential(PoseStack poseStack, Vec3 cameraPos, MultiBufferSource.BufferSource bufferSource) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }

        Level level = minecraft.level;
        BlockPos playerPos = minecraft.player.blockPosition();
        VertexConsumer consumer = bufferSource.getBuffer(RenderType.lines());

        int minX = ((playerPos.getX() - RENDER_RADIUS) / SAMPLE_SPACING) * SAMPLE_SPACING;
        int maxX = ((playerPos.getX() + RENDER_RADIUS) / SAMPLE_SPACING) * SAMPLE_SPACING;
        int minZ = ((playerPos.getZ() - RENDER_RADIUS) / SAMPLE_SPACING) * SAMPLE_SPACING;
        int maxZ = ((playerPos.getZ() + RENDER_RADIUS) / SAMPLE_SPACING) * SAMPLE_SPACING;

        for (int worldX = minX; worldX <= maxX; worldX += SAMPLE_SPACING) {
            for (int worldZ = minZ; worldZ <= maxZ; worldZ += SAMPLE_SPACING) {
                double potential = generator.calculateRiverPotential(worldX, worldZ);
                int terrainHeight = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ);

                renderPotentialMarker(poseStack, consumer, cameraPos, worldX, terrainHeight, worldZ, potential);
            }
        }
    }

    private static void renderPotentialMarker(PoseStack poseStack, VertexConsumer consumer, Vec3 cameraPos,
                                              double worldX, double worldY, double worldZ, double potential) {
        double renderX = worldX - cameraPos.x;
        double renderY = worldY - cameraPos.y;
        double renderZ = worldZ - cameraPos.z;

        float red = (float) potential;
        float blue = 1.0f - red;
        float green = 0.0f;

        poseStack.pushPose();
        Matrix4f matrix = poseStack.last().pose();

        float x = (float) renderX;
        float y = (float) renderY;
        float z = (float) renderZ;
        float height = BEAM_HEIGHT;

        addVertex(consumer, matrix, x, y, z, red, green, blue);
        addVertex(consumer, matrix, x, y + height, z, red, green, blue);

        addVertex(consumer, matrix, x + 0.5f, y, z, red, green, blue);
        addVertex(consumer, matrix, x + 0.5f, y + height, z, red, green, blue);

        addVertex(consumer, matrix, x, y, z + 0.5f, red, green, blue);
        addVertex(consumer, matrix, x, y + height, z + 0.5f, red, green, blue);

        addVertex(consumer, matrix, x + 0.5f, y, z + 0.5f, red, green, blue);
        addVertex(consumer, matrix, x + 0.5f, y + height, z + 0.5f, red, green, blue);

        poseStack.popPose();
    }

    private static void addVertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z,
                                  float red, float green, float blue) {
        consumer.vertex(matrix, x, y, z)
                .color(red, green, blue, 1.0f)
                .normal(0.0f, 1.0f, 0.0f)
                .endVertex();
    }
}
