package org.sosly.rivertale.client;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.Collection;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.sosly.rivertale.cell.feature.Feature;

@OnlyIn(Dist.CLIENT)
final class RiverCellLabels {
    private static final int GRAY = 0xFFD0D0D0;
    private static final int ORANGE = 0xFFFFA044;
    private static final int CYAN = 0xFF00FFFF;
    private static final double INSET = 0.75;
    private static final double Y_OFFSET = 20.0;
    private static final float SCALE = 0.17f;
    private static final int LINE_SPACING = 11;
    private static final String CELL_Y_PREFIX = "CellY: ";

    private RiverCellLabels() {}

    static void render(Collection<ClientRegionCache.Region> regions, PoseStack pose, BufferBuilder builder, Vec3 camera) {
        Font font = Minecraft.getInstance().font;
        MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(builder);
        for (ClientRegionCache.Region region : regions) {
            for (ClientRegionCache.Cell cell : region.cells()) {
                renderLabel(cell, pose, camera, font, buffer);
            }
        }
        buffer.endBatch();
    }

    private static void renderLabel(ClientRegionCache.Cell cell, PoseStack pose, Vec3 camera, Font font,
                                    MultiBufferSource buffer) {
        if (cell.averageY().isEmpty() && cell.feature() != Feature.NONE) {
            return;
        }

        int averageY = cell.averageY().orElse(cell.y());
        pose.pushPose();
        pose.translate(cell.pos().getMinBlockX() + INSET - camera.x, averageY + Y_OFFSET - camera.y,
            cell.pos().getMinBlockZ() + INSET - camera.z);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(SCALE, SCALE, SCALE);
        draw(font, buffer, pose, "Class: " + cell.feature().name(), 0, 0, GRAY);
        draw(font, buffer, pose, "AverageY: " + averageY, 0, LINE_SPACING, GRAY);
        if (cell.averageY().isPresent()) {
            draw(font, buffer, pose, CELL_Y_PREFIX, 0, 2 * LINE_SPACING, GRAY);
            draw(font, buffer, pose, Integer.toString(cell.y()), font.width(CELL_Y_PREFIX), 2 * LINE_SPACING,
                cellYColor(cell.y(), averageY));
        }
        if (cell.flow().isPresent()) {
            draw(font, buffer, pose, "Flow: " + cell.flow().getAsDouble(), 0, 3 * LINE_SPACING, GRAY);
        }
        pose.popPose();
    }

    private static void draw(Font font, MultiBufferSource buffer, PoseStack pose, String text, int column, int row,
                             int color) {
        font.drawInBatch(text, column, row, color, false, pose.last().pose(), buffer, Font.DisplayMode.SEE_THROUGH,
            0, LightTexture.FULL_BRIGHT);
    }

    static int cellYColor(int cellY, int averageY) {
        if (cellY > averageY) {
            return ORANGE;
        }
        return cellY < averageY ? CYAN : GRAY;
    }
}
