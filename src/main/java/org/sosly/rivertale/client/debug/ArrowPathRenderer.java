package org.sosly.rivertale.client.debug;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.List;

public class ArrowPathRenderer {

    private static final double MAX_RENDER_DISTANCE_SQ = 80.0 * 80.0;

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, Camera camera) {
        if (!DebugVisualizationManager.isEnabled()) {
            return;
        }

        List<Vec3> path = DebugVisualizationManager.getArrowPath();
        if (path.isEmpty()) {
            return;
        }

        Vec3 cameraPos = camera.getPosition();
        if (!shouldRenderPath(path, cameraPos)) {
            return;
        }

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.debugLineStrip(2.0));
        renderPath(poseStack, consumer, path, cameraPos);
    }

    private static boolean shouldRenderPath(List<Vec3> path, Vec3 cameraPos) {
        if (path.isEmpty()) {
            return false;
        }

        Vec3 firstPoint = path.get(0);
        double dx = firstPoint.x - cameraPos.x;
        double dy = firstPoint.y - cameraPos.y;
        double dz = firstPoint.z - cameraPos.z;
        double distSq = dx * dx + dy * dy + dz * dz;

        return distSq <= MAX_RENDER_DISTANCE_SQ;
    }

    private static void renderPath(PoseStack poseStack, VertexConsumer consumer,
                                   List<Vec3> path, Vec3 cameraPos) {
        for (int i = 0; i < path.size(); i++) {
            Vec3 point = path.get(i);

            float hue = (float) i / (float) path.size() * 0.15f + 0.5f;
            int color = Mth.hsvToRgb(hue, 0.9f, 0.9f);
            int red = (color >> 16) & 255;
            int green = (color >> 8) & 255;
            int blue = color & 255;

            consumer.vertex(
                poseStack.last().pose(),
                (float) (point.x - cameraPos.x),
                (float) (point.y - cameraPos.y),
                (float) (point.z - cameraPos.z)
            ).color(red, green, blue, 255).endVertex();
        }
    }
}
